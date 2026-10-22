package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.messaging.kafka.KafkaConfiguration;
import ch.admin.bit.jeap.messaging.kafka.spring.JeapKafkaPropertyFactory;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Keeps the progress of the dead letter reactivation from expiring. The consumer of a reactivation run only
 * subscribes to the dead letter topic while it runs, so the broker removes the group's committed offsets for the
 * topic after {@code offsets.retention.minutes} without a commit (7 days by default). The next run would then start
 * at the earliest message of the topic and forward messages again that have already been processed, and the pending
 * message count would jump back up.
 * <p>
 * This job commits the existing offsets again, unchanged, which restarts the broker's retention timer.
 * The group also contains the error event listeners, and the broker only accepts commits from non-members for an
 * empty group - and this group is never empty in production - so the commit is made by a short-lived consumer that
 * joins the group, like a reactivation run does. The job is skipped while any group member is assigned a partition
 * of the dead letter topic (an active reactivation run), so it never takes partitions away from a run. Joining the
 * group itself is serialized against a reactivation run's own subscribe via {@link DeadLetterTopicSubscribeLock},
 * so the two can never join at the same moment and have the broker split the topic's partitions between them.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "jeap.errorhandling.dead-letter-offset-keep-alive.enabled", havingValue = "true", matchIfMissing = true)
public class DeadLetterOffsetKeepAlive {

    private static final long KAFKA_TIMEOUT_SECONDS = 30;
    private static final Duration ASSIGNMENT_TIMEOUT = Duration.ofSeconds(30);

    private final String deadLetterTopicName;
    private final KafkaConfiguration kafkaConfiguration;
    private final String defaultClusterName;
    private final LockProvider lockProvider;

    private final ThreadPoolExecutor renewExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), r -> {
        Thread thread = new Thread(r, "dead-letter-offset-keep-alive");
        thread.setDaemon(true);
        return thread;
    }, new ThreadPoolExecutor.DiscardPolicy());

    public DeadLetterOffsetKeepAlive(@Value("${jeap.errorhandling.deadLetterTopicName}") String deadLetterTopicName,
                                     KafkaConfiguration kafkaConfiguration,
                                     Environment environment,
                                     LockProvider lockProvider) {
        this.deadLetterTopicName = deadLetterTopicName;
        this.kafkaConfiguration = kafkaConfiguration;
        this.defaultClusterName = JeapKafkaPropertyFactory.createJeapKafkaProperties(environment).getDefaultClusterName();
        this.lockProvider = lockProvider;
    }

    /**
     * Checks frequently whether a renewal is due, but the ShedLock held for the configured interval lets only one
     * instance renew per interval. The lock is stored in the database, so this holds however often the instances
     * restart, which an in-memory timer of a single instance would not. The actual renewal, with its Kafka round
     * trips and lock waits, runs on a dedicated thread, because it must not block Spring's single-threaded
     * scheduler and delay other jobs. The lock is held regardless (see {@code lockAtLeastFor} above), so dispatching
     * and returning immediately does not affect how often an instance may renew.
     */
    @Scheduled(fixedDelayString = "${jeap.errorhandling.dead-letter-offset-keep-alive.check-interval:PT3H}",
            initialDelayString = "${jeap.errorhandling.dead-letter-offset-keep-alive.initial-delay:PT10M}")
    @SchedulerLock(name = "dead-letter-offset-keep-alive",
            lockAtLeastFor = "${jeap.errorhandling.dead-letter-offset-keep-alive.interval:PT24H}",
            lockAtMostFor = "${jeap.errorhandling.dead-letter-offset-keep-alive.interval:PT24H}")
    public void renewCommittedOffsets() {
        renewExecutor.execute(this::doRenewCommittedOffsets);
    }

    @PreDestroy
    void shutdown() {
        renewExecutor.shutdownNow();
    }

    private void doRenewCommittedOffsets() {
        Object groupId = kafkaConfiguration.consumerConfig(defaultClusterName).get(ConsumerConfig.GROUP_ID_CONFIG);
        if (groupId == null) {
            log.warn("No consumer group id configured, the committed offsets of dead letter topic {} are not renewed", deadLetterTopicName);
            return;
        }
        renewCommittedOffsets(groupId.toString(), deadLetterTopicName);
    }

    /**
     * Commits the committed offsets of the group for the topic again, unchanged.
     *
     * @return the number of partitions whose offset was renewed, 0 if there was nothing to renew or the round was skipped
     */
    int renewCommittedOffsets(String groupId, String topic) {
        try (Admin admin = Admin.create(kafkaConfiguration.adminConfig(defaultClusterName))) {
            if (!hasCommittedOffsets(admin, groupId, topic)) {
                log.debug("Consumer group {} has no committed offsets on dead letter topic {}, nothing to renew", groupId, topic);
                return 0;
            }
            return renewAsGroupMember(admin, groupId, topic);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            log.warn("Failed to renew the committed offsets of consumer group {} on dead letter topic {}", groupId, topic, e);
            return 0;
        }
    }

    private int renewAsGroupMember(Admin admin, String groupId, String topic) throws ExecutionException, InterruptedException, TimeoutException {
        Optional<SimpleLock> lock = DeadLetterTopicSubscribeLock.await(lockProvider);
        if (lock.isEmpty()) {
            log.info("Committed offsets of consumer group {} on dead letter topic {} not renewed, could not get the dead " +
                    "letter topic subscribe lock. Next attempt at the next interval.", groupId, topic);
            return 0;
        }
        if (isTopicAssignedToGroupMember(admin, groupId, topic)) {
            lock.get().unlock();
            log.info("Committed offsets of consumer group {} on dead letter topic {} not renewed, a reactivation run is active. " +
                    "Next attempt at the next interval.", groupId, topic);
            return 0;
        }
        // commitAsGroupMember releases the lock itself, as soon as the first assignment is resolved - not for its
        // whole duration - so it does not needlessly make a concurrently starting reactivation run wait for it
        return commitAsGroupMember(groupId, topic, lock.get());
    }

    private boolean hasCommittedOffsets(Admin admin, String groupId, String topic) throws ExecutionException, InterruptedException, TimeoutException {
        return admin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata()
                .get(KAFKA_TIMEOUT_SECONDS, TimeUnit.SECONDS).entrySet().stream()
                .anyMatch(entry -> entry.getKey().topic().equals(topic) && entry.getValue() != null);
    }

    private boolean isTopicAssignedToGroupMember(Admin admin, String groupId, String topic) throws ExecutionException, InterruptedException, TimeoutException {
        ConsumerGroupDescription group = admin.describeConsumerGroups(List.of(groupId)).describedGroups()
                .get(groupId).get(KAFKA_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return group.members().stream()
                .flatMap(member -> member.assignment().topicPartitions().stream())
                .anyMatch(partition -> partition.topic().equals(topic));
    }

    private int commitAsGroupMember(String groupId, String topic, SimpleLock subscribeLock) {
        Map<String, Object> props = new HashMap<>(kafkaConfiguration.consumerConfig(defaultClusterName));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
        boolean locked = true;
        try (KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + ASSIGNMENT_TIMEOUT.toNanos();
            while (consumer.assignment().isEmpty() && System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(200));
            }
            // Subscribing joined the consumer group, triggering a rebalance - the subscribe lock held since
            // before that is only needed until the resulting assignment is resolved (here), not for the rest of
            // this method, so a concurrently starting reactivation run does not need to wait any longer than that.
            subscribeLock.unlock();
            locked = false;
            Set<TopicPartition> assigned = consumer.assignment();
            if (assigned.isEmpty()) {
                log.warn("Consumer group {} did not assign a partition of dead letter topic {} within {}, the committed offsets " +
                        "were not renewed", groupId, topic, ASSIGNMENT_TIMEOUT);
                return 0;
            }
            // only the committed offsets are re-committed, the position reached by polling is irrelevant
            Map<TopicPartition, OffsetAndMetadata> toRenew = consumer.committed(assigned).entrySet().stream()
                    .filter(entry -> entry.getValue() != null)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            if (!toRenew.isEmpty()) {
                consumer.commitSync(toRenew);
            }
            log.info("Renewed the committed offsets {} of consumer group {} on dead letter topic {}", toRenew, groupId, topic);
            return toRenew.size();
        } finally {
            if (locked) {
                subscribeLock.unlock();
            }
        }
    }
}
