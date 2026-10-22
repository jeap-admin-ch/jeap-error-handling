package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.messaging.kafka.KafkaConfiguration;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.messaging.kafka.spring.JeapKafkaPropertyFactory;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks the number of messages pending on the dead letter topic (end offset minus committed consumer group
 * offset, summed over all partitions), for display in the UI and as a metric. The count is determined with a
 * short-lived consumer that is never subscribed or assigned to the topic, so it never joins the consumer group
 * used by {@link DeadLetterReactivationRun} or fetches/commits anything - only topic and offset metadata is read.
 */
@Component
@Slf4j
public class DeadLetterTopicMetrics {

    // upper bound for the blocking metadata calls, so a slow broker cannot block the refresh thread indefinitely
    private static final Duration KAFKA_API_TIMEOUT = Duration.ofSeconds(15);

    static final String PENDING_MESSAGES_GAUGE_METRIC = "eh_deadletter_pending_messages";

    private final String deadLetterTopicName;
    private final KafkaConfiguration kafkaConfiguration;
    private final String defaultClusterName;

    private final ThreadPoolExecutor refreshExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), r -> {
        Thread thread = new Thread(r, "dead-letter-count-refresh");
        thread.setDaemon(true);
        return thread;
    }, new ThreadPoolExecutor.DiscardPolicy());

    private volatile long pendingMessageCount = -1;
    // Guards against a slow, superseded refresh() overwriting the result of a later one that already
    // completed - e.g. a scheduled refresh in flight when a reactivation finishes and triggers its own
    // refresh must not clobber the reactivation's up-to-date result once it eventually completes.
    private final AtomicLong refreshGeneration = new AtomicLong();

    public DeadLetterTopicMetrics(@Value("${jeap.errorhandling.deadLetterTopicName}") String deadLetterTopicName,
                                   KafkaConfiguration kafkaConfiguration,
                                   MeterRegistry meterRegistry,
                                   Environment environment) {
        this.deadLetterTopicName = deadLetterTopicName;
        this.kafkaConfiguration = kafkaConfiguration;
        KafkaProperties kafkaProperties = JeapKafkaPropertyFactory.createJeapKafkaProperties(environment);
        this.defaultClusterName = kafkaProperties.getDefaultClusterName();
        // NaN instead of the -1 sentinel until the first count is known, so that it does not distort aggregations
        Gauge.builder(PENDING_MESSAGES_GAUGE_METRIC, () -> pendingMessageCount < 0 ? Double.NaN : pendingMessageCount)
                .description("Messages currently pending on the dead letter topic")
                .register(meterRegistry);
    }

    /**
     * Triggers a refresh periodically, not on application startup, to avoid blocking it on a Kafka round-trip.
     * The refresh runs on a dedicated thread, because the Kafka round-trip must not block Spring's single-threaded
     * scheduler and delay other jobs. If a refresh is still running, at most one further refresh is queued.
     */
    @Scheduled(fixedRateString = "${jeap.errorhandling.dead-letter-count.updateFrequencyMillis:30000}")
    void scheduledRefresh() {
        refreshExecutor.execute(this::refresh);
    }

    @PreDestroy
    void shutdown() {
        refreshExecutor.shutdownNow();
    }

    /**
     * Re-determines the number of pending messages. Called by the scheduled refresh, and once a dead letter
     * reactivation run has finished so the UI reflects the consumption without waiting for the next refresh.
     */
    public void refresh() {
        long generation = refreshGeneration.incrementAndGet();
        try {
            long count = countPendingMessages();
            // Discard the result if a later refresh() has started meanwhile, even if that later one is
            // still running: it will produce a more up-to-date result than this one.
            if (generation == refreshGeneration.get()) {
                pendingMessageCount = count;
            }
        } catch (RuntimeException e) {
            log.warn("Failed to determine the number of messages pending on dead letter topic {}, keeping the last known value {}",
                    deadLetterTopicName, pendingMessageCount, e);
        }
    }

    public long getPendingMessageCount() {
        return pendingMessageCount;
    }

    private long countPendingMessages() {
        Map<String, Object> props = new HashMap<>(kafkaConfiguration.consumerConfig(defaultClusterName));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, (int) KAFKA_API_TIMEOUT.toMillis());
        props.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) KAFKA_API_TIMEOUT.toMillis());
        try (KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(props)) {
            List<PartitionInfo> partitionInfos = consumer.partitionsFor(deadLetterTopicName);
            if (partitionInfos == null || partitionInfos.isEmpty()) {
                return 0;
            }
            Set<TopicPartition> partitions = new HashSet<>();
            partitionInfos.forEach(info -> partitions.add(new TopicPartition(info.topic(), info.partition())));
            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);
            Map<TopicPartition, Long> beginningOffsets = consumer.beginningOffsets(partitions);
            Map<TopicPartition, OffsetAndMetadata> committed = consumer.committed(partitions);
            long pending = 0;
            for (TopicPartition partition : partitions) {
                long position = DeadLetterOffsets.committedPosition(beginningOffsets, partition, committed.get(partition));
                pending += Math.max(0, endOffsets.getOrDefault(partition, position) - position);
            }
            return pending;
        }
    }
}
