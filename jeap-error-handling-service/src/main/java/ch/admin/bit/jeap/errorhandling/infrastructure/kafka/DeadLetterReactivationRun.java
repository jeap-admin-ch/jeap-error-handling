package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.RebalanceInProgressException;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;

/**
 * One reactivation run: forwards the messages that are on the dead letter topic when the run starts to the error
 * topic, using one consumer in the consumer group of the EHS and one producer for the whole run. Progress is
 * committed to the consumer group after each batch has been acknowledged by the error topic (at-least-once).
 * Not thread-safe, a run instance must only be executed once.
 */
@Slf4j
class DeadLetterReactivationRun {

    static final Duration POLL_TIMEOUT = Duration.ofSeconds(1);
    static final Duration PROGRESS_LOG_INTERVAL = Duration.ofSeconds(10);

    enum EndReason {
        MAX_RECORDS("max-records"),
        BOUNDARY_REACHED("boundary-reached"),
        IDLE_TIMEOUT("idle-timeout"),
        CONCURRENT_RUN("concurrent-run"),
        SEND_FAILED("send-failed"),
        COMMIT_FAILED("commit-failed"),
        ERROR("error");

        private final String tagValue;

        EndReason(String tagValue) {
            this.tagValue = tagValue;
        }

        String tagValue() {
            return tagValue;
        }
    }

    record Result(long forwarded, EndReason endReason, Duration duration) {
    }

    private final Consumer<byte[], byte[]> consumer;
    private final Producer<byte[], byte[]> producer;
    private final String deadLetterTopic;
    private final String errorTopic;
    private final long maxRecords;
    private final Duration idleTimeout;
    private final IntConsumer forwardedListener;
    private final LockProvider lockProvider;

    private final Map<TopicPartition, Long> endOffsets = new HashMap<>();
    private final Map<TopicPartition, Long> beginningOffsets = new HashMap<>();
    private final Set<TopicPartition> assignment = new HashSet<>();
    private boolean assignmentReceived;
    private boolean assignmentChanged;

    private long forwarded;
    private Map<TopicPartition, OffsetAndMetadata> pendingCommit;
    private int pendingCommitCount;
    private RuntimeException commitFailure;
    private long pendingAtStart;
    private long startNanos;
    private long lastProgressNanos;
    private long lastProgressLogNanos;
    private long lastProgressSum = -1;

    /**
     * @param forwardedListener called with the number of messages of each batch that has been acknowledged by the
     *                          error topic and committed to the consumer group
     */
    DeadLetterReactivationRun(Consumer<byte[], byte[]> consumer, Producer<byte[], byte[]> producer,
                              String deadLetterTopic, String errorTopic, long maxRecords, Duration idleTimeout,
                              IntConsumer forwardedListener, LockProvider lockProvider) {
        this.consumer = consumer;
        this.producer = producer;
        this.deadLetterTopic = deadLetterTopic;
        this.errorTopic = errorTopic;
        this.maxRecords = maxRecords;
        this.idleTimeout = idleTimeout;
        this.forwardedListener = forwardedListener;
        this.lockProvider = lockProvider;
    }

    Result execute() {
        startNanos = System.nanoTime();
        lastProgressNanos = startNanos;
        lastProgressLogNanos = startNanos;
        EndReason endReason;
        try {
            endReason = run();
        } catch (SendFailedException e) {
            log.warn("Dead letter reactivation: publishing a message to topic {} failed, the batch is not committed " +
                     "and will be forwarded again by the next run", errorTopic, e.getCause());
            endReason = EndReason.SEND_FAILED;
        } catch (CommitFailedException | RebalanceInProgressException e) {
            log.warn("Dead letter reactivation: committing the progress failed because the consumer group was " +
                     "rebalanced, the last batch will be forwarded again by the next run", e);
            endReason = EndReason.COMMIT_FAILED;
        } catch (RuntimeException e) {
            log.error("Dead letter reactivation failed", e);
            endReason = EndReason.ERROR;
        }
        Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
        logEnd(endReason, duration);
        return new Result(forwarded, endReason, duration);
    }

    private EndReason run() {
        captureBoundary();
        if (pendingAtStart == 0) {
            return EndReason.BOUNDARY_REACHED;
        }
        log.info("Dead letter reactivation started: {} messages pending on topic {}, forwarding at most {} to topic {}",
                pendingAtStart, deadLetterTopic, maxRecords, errorTopic);

        Optional<SimpleLock> subscribeLock = DeadLetterTopicSubscribeLock.await(lockProvider);
        if (subscribeLock.isEmpty()) {
            log.warn("Dead letter reactivation: could not get the dead letter topic subscribe lock, aborting this run");
            return EndReason.ERROR;
        }
        consumer.subscribe(List.of(deadLetterTopic), new AssignmentListener());
        SimpleLock lockToRelease = subscribeLock.get();
        long subscribeLockDeadline = System.nanoTime() + DeadLetterTopicSubscribeLock.MAX_DURATION.toNanos();
        try {
            while (true) {
                ConsumerRecords<byte[], byte[]> records = consumer.poll(POLL_TIMEOUT);
                EndReason endReason = processRecords(records);
                // Subscribing joined the consumer group, triggering a rebalance. Holding the dead letter topic
                // subscribe lock from before that until the resulting assignment is resolved (here) excludes
                // DeadLetterOffsetKeepAlive, which also joins the group transiently, from subscribing at the same
                // moment and splitting the dead letter topic's partitions with this run on the rebalance - see
                // DeadLetterTopicSubscribeLock. The records of the poll that resolves the assignment are still
                // processed above before releasing the lock, so none of them are lost.
                if (lockToRelease != null && (assignmentReceived || System.nanoTime() >= subscribeLockDeadline)) {
                    lockToRelease.unlock();
                    lockToRelease = null;
                }
                if (endReason != null) {
                    return endReason;
                }
            }
        } finally {
            if (lockToRelease != null) {
                lockToRelease.unlock();
            }
        }
    }

    private EndReason processRecords(ConsumerRecords<byte[], byte[]> records) {
        if (commitFailure != null) {
            throw commitFailure;
        }
        if (pendingCommit != null) {
            commitPending();
        }
        EndReason assignmentEndReason = processAssignmentChange();
        if (assignmentEndReason != null) {
            return assignmentEndReason;
        }
        forwardBatch(records);
        EndReason completionReason = completionReason();
        if (completionReason != null) {
            return completionReason;
        }
        if (System.nanoTime() - lastProgressNanos > idleTimeout.toNanos()) {
            log.warn("Dead letter reactivation made no progress for {}, ending the run", idleTimeout);
            return EndReason.IDLE_TIMEOUT;
        }
        logProgressIfDue();
        return null;
    }

    private EndReason processAssignmentChange() {
        if (!assignmentChanged) {
            return null;
        }
        assignmentChanged = false;
        markProgress();
        if (!assignmentReceived) {
            assignmentReceived = true;
            if (assignment.isEmpty()) {
                log.info("Dead letter reactivation: no partition of topic {} assigned, a concurrent run " +
                         "continues the reactivation", deadLetterTopic);
                return EndReason.CONCURRENT_RUN;
            }
        }
        return null;
    }

    private EndReason completionReason() {
        if (pendingCommit != null) {
            return null;
        }
        if (forwarded >= maxRecords) {
            return EndReason.MAX_RECORDS;
        }
        if (boundaryReached()) {
            return EndReason.BOUNDARY_REACHED;
        }
        return null;
    }

    private void captureBoundary() {
        List<PartitionInfo> partitionInfos = consumer.partitionsFor(deadLetterTopic);
        if (partitionInfos == null || partitionInfos.isEmpty()) {
            log.warn("Dead letter reactivation: topic {} not found or has no partitions", deadLetterTopic);
            return;
        }
        Set<TopicPartition> partitions = new HashSet<>();
        partitionInfos.forEach(info -> partitions.add(new TopicPartition(info.topic(), info.partition())));
        endOffsets.putAll(consumer.endOffsets(partitions));
        Map<TopicPartition, OffsetAndMetadata> committed = consumer.committed(partitions);
        beginningOffsets.putAll(consumer.beginningOffsets(partitions));
        for (TopicPartition partition : partitions) {
            pendingAtStart += Math.max(0, endOffsets.get(partition) - committedPosition(partition, committed.get(partition)));
        }
    }

    private void forwardBatch(ConsumerRecords<byte[], byte[]> records) {
        if (records.isEmpty()) {
            return;
        }
        AtomicReference<Exception> sendFailure = new AtomicReference<>();
        Map<TopicPartition, OffsetAndMetadata> offsetsToCommit = new HashMap<>();
        int batchCount = 0;
        for (TopicPartition partition : records.partitions()) {
            long endOffset = endOffsets.getOrDefault(partition, 0L);
            for (ConsumerRecord<byte[], byte[]> data : records.records(partition)) {
                if (forwardingLimitReached(partition, data.offset(), endOffset, batchCount)) {
                    break;
                }
                send(data, sendFailure);
                offsetsToCommit.put(partition, new OffsetAndMetadata(data.offset() + 1));
                batchCount++;
            }
        }
        if (batchCount == 0) {
            return;
        }

        producer.flush();
        if (sendFailure.get() != null) {
            throw new SendFailedException(sendFailure.get());
        }
        // A batch whose commit is still pending because of a rebalance is committed together with this batch
        if (pendingCommit == null) {
            pendingCommit = new HashMap<>();
        }
        pendingCommit.putAll(offsetsToCommit);
        pendingCommitCount += batchCount;
        commitPending();
    }

    private boolean forwardingLimitReached(TopicPartition partition, long offset, long endOffset, int batchCount) {
        if (offset >= endOffset) {
            consumer.pause(List.of(partition));
            return true;
        }
        return forwarded + pendingCommitCount + batchCount >= maxRecords;
    }

    /**
     * Commits the offsets of the last forwarded batch. If the consumer group is being rebalanced, the commit is
     * retried after the next poll, at the latest when the partitions are revoked.
     */
    private void commitPending() {
        try {
            consumer.commitSync(pendingCommit);
        } catch (RebalanceInProgressException e) {
            log.debug("Dead letter reactivation: consumer group rebalance in progress, retrying the commit after the next poll");
            return;
        }
        log.debug("Dead letter reactivation: forwarded and committed a batch of {} messages, offsets {}", pendingCommitCount, pendingCommit);
        forwarded += pendingCommitCount;
        forwardedListener.accept(pendingCommitCount);
        pendingCommit = null;
        pendingCommitCount = 0;
        markProgress();
    }

    private void send(ConsumerRecord<byte[], byte[]> data, AtomicReference<Exception> sendFailure) {
        ProducerRecord<byte[], byte[]> producerRecord = data.key() != null
                ? new ProducerRecord<>(errorTopic, data.key(), data.value())
                : new ProducerRecord<>(errorTopic, data.value());
        data.headers().forEach(header -> producerRecord.headers().add(header));
        try {
            producer.send(producerRecord, (metadata, exception) -> {
                if (exception != null) {
                    sendFailure.compareAndSet(null, exception);
                }
            });
        } catch (RuntimeException e) {
            throw new SendFailedException(e);
        }
    }

    /**
     * The run boundary is reached when every partition of the dead letter topic has been read up to the end offset
     * captured at the start of the run, either by this run (assigned partitions) or by a concurrent run (committed
     * offsets of the partitions not assigned to this run).
     */
    private boolean boundaryReached() {
        if (!assignmentReceived) {
            return false;
        }
        long progressSum = 0;
        boolean done = true;
        Set<TopicPartition> unassigned = new HashSet<>();
        for (Map.Entry<TopicPartition, Long> entry : endOffsets.entrySet()) {
            TopicPartition partition = entry.getKey();
            if (assignment.contains(partition)) {
                long position = consumer.position(partition);
                progressSum += position;
                if (position < entry.getValue()) {
                    done = false;
                } else {
                    consumer.pause(List.of(partition));
                }
            } else {
                unassigned.add(partition);
            }
        }
        if (done && !unassigned.isEmpty()) {
            BoundaryProgress progress = committedBoundaryProgress(unassigned);
            progressSum += progress.positionSum();
            done = progress.reached();
        }
        updateProgress(progressSum);
        return done;
    }

    private BoundaryProgress committedBoundaryProgress(Set<TopicPartition> partitions) {
        Map<TopicPartition, OffsetAndMetadata> committed = consumer.committed(partitions);
        long progressSum = 0;
        boolean done = true;
        for (TopicPartition partition : partitions) {
            long position = committedPosition(partition, committed.get(partition));
            progressSum += position;
            if (position < endOffsets.get(partition)) {
                done = false;
            }
        }
        return new BoundaryProgress(progressSum, done);
    }

    private record BoundaryProgress(long positionSum, boolean reached) {
    }

    private void updateProgress(long progressSum) {
        if (progressSum > lastProgressSum) {
            if (lastProgressSum >= 0) {
                markProgress();
            }
            lastProgressSum = progressSum;
        }
    }

    /**
     * The position a consumer of the group starts at: the committed offset, or the earliest available offset if
     * there is no committed offset or the committed offset is no longer available.
     */
    private long committedPosition(TopicPartition partition, OffsetAndMetadata committed) {
        return DeadLetterOffsets.committedPosition(beginningOffsets, partition, committed);
    }

    private void markProgress() {
        lastProgressNanos = System.nanoTime();
    }

    private void logProgressIfDue() {
        long now = System.nanoTime();
        if (now - lastProgressLogNanos >= PROGRESS_LOG_INTERVAL.toNanos()) {
            lastProgressLogNanos = now;
            log.info("Dead letter reactivation in progress: forwarded {} messages, {} pending, {} messages/s",
                    forwarded, Math.max(0, Math.min(pendingAtStart, maxRecords) - forwarded),
                    rate(Duration.ofNanos(now - startNanos)));
        }
    }

    private void logEnd(EndReason endReason, Duration duration) {
        String message = "Dead letter reactivation ended ({}): forwarded {} messages from topic {} to topic {} in {} ms ({} messages/s)";
        Object[] args = {endReason.tagValue(), forwarded, deadLetterTopic, errorTopic, duration.toMillis(), rate(duration)};
        switch (endReason) {
            case MAX_RECORDS, BOUNDARY_REACHED, CONCURRENT_RUN -> log.info(message, args);
            default -> log.warn(message, args);
        }
    }

    private String rate(Duration duration) {
        long millis = Math.max(1, duration.toMillis());
        return String.format(Locale.ROOT, "%.1f", forwarded * 1000.0 / millis);
    }

    private class AssignmentListener implements ConsumerRebalanceListener {
        @Override
        public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
            assignment.removeAll(partitions);
            if (pendingCommit != null) {
                try {
                    commitPending();
                } catch (RuntimeException e) {
                    commitFailure = e;
                }
            }
        }

        @Override
        public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
            assignment.addAll(partitions);
            assignmentChanged = true;
            List<TopicPartition> finished = partitions.stream()
                    .filter(partition -> consumer.position(partition) >= endOffsets.getOrDefault(partition, 0L))
                    .toList();
            consumer.pause(finished);
        }

        @Override
        public void onPartitionsLost(Collection<TopicPartition> partitions) {
            assignment.removeAll(partitions);
            if (pendingCommit != null && partitions.stream().anyMatch(pendingCommit::containsKey)) {
                pendingCommit = null;
                pendingCommitCount = 0;
                commitFailure = new CommitFailedException(
                        "Dead letter reactivation lost partitions with uncommitted progress: " + partitions);
            }
        }
    }

    private static class SendFailedException extends RuntimeException {
        SendFailedException(Throwable cause) {
            super(cause);
        }
    }
}
