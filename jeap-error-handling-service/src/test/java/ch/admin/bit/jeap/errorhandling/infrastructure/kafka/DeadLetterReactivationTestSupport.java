package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.messaging.kafka.KafkaConfiguration;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsSpec;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Helpers for dead letter reactivation tests that work with dedicated topics and raw byte messages.
 */
class DeadLetterReactivationTestSupport {

    private final KafkaConfiguration kafkaConfiguration;
    private final KafkaAdmin kafkaAdmin;

    DeadLetterReactivationTestSupport(KafkaConfiguration kafkaConfiguration, KafkaAdmin kafkaAdmin) {
        this.kafkaConfiguration = kafkaConfiguration;
        this.kafkaAdmin = kafkaAdmin;
    }

    static String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    void createTopic(String name, int partitions, Map<String, String> configs) {
        kafkaAdmin.createOrModifyTopics(TopicBuilder.name(name).partitions(partitions).replicas(1).configs(configs).build());
    }

    void createTopic(String name, int partitions) {
        createTopic(name, partitions, Map.of());
    }

    void produce(String topic, int count) {
        produce(topic, count, null, 0);
    }

    /**
     * Produces messages with the values "message-0", "message-1", ... to the given partition (or the default
     * partitioner if null), padded with the given number of bytes.
     */
    void produce(String topic, int count, Integer partition, int paddingBytes) {
        produceValues(topic, partition, valuesOf(count, paddingBytes));
    }

    void produceValues(String topic, Integer partition, List<byte[]> values) {
        try (KafkaProducer<byte[], byte[]> producer = createProducer()) {
            for (byte[] value : values) {
                producer.send(new ProducerRecord<>(topic, partition, null, value));
            }
            producer.flush();
        }
    }

    static List<byte[]> valuesOf(int count, int paddingBytes) {
        List<byte[]> values = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            values.add(("message-" + UUID.randomUUID() + "-" + "x".repeat(paddingBytes)).getBytes(StandardCharsets.UTF_8));
        }
        return values;
    }

    long countMessages(String topic) {
        try (KafkaConsumer<byte[], byte[]> consumer = createConsumer()) {
            List<TopicPartition> partitions = partitions(consumer, topic);
            Map<TopicPartition, Long> end = consumer.endOffsets(partitions);
            Map<TopicPartition, Long> beginning = consumer.beginningOffsets(partitions);
            return partitions.stream().mapToLong(p -> end.get(p) - beginning.get(p)).sum();
        }
    }

    List<String> readValues(String topic) {
        try (KafkaConsumer<byte[], byte[]> consumer = createConsumer()) {
            List<TopicPartition> partitions = partitions(consumer, topic);
            Map<TopicPartition, Long> end = consumer.endOffsets(partitions);
            long expected = countMessages(topic);
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            List<String> values = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 30_000;
            while (values.size() < expected && System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<byte[], byte[]> data : consumer.poll(Duration.ofMillis(500))) {
                    if (data.offset() < end.get(new TopicPartition(data.topic(), data.partition()))) {
                        values.add(new String(data.value(), StandardCharsets.UTF_8));
                    }
                }
            }
            return values;
        }
    }

    /**
     * @return the committed offsets of the consumer group, by partition number of the given topic
     */
    Map<Integer, Long> committedOffsets(String groupId, String topic) throws Exception {
        try (Admin admin = Admin.create(kafkaConfiguration.adminConfig(KafkaProperties.DEFAULT_CLUSTER))) {
            Map<TopicPartition, OffsetAndMetadata> offsets = admin
                    .listConsumerGroupOffsets(Map.of(groupId, new ListConsumerGroupOffsetsSpec()))
                    .partitionsToOffsetAndMetadata(groupId).get();
            Map<Integer, Long> result = new HashMap<>();
            offsets.forEach((partition, offset) -> {
                if (partition.topic().equals(topic) && offset != null) {
                    result.put(partition.partition(), offset.offset());
                }
            });
            return result;
        }
    }

    /**
     * Creates a consumer of the given group that is subscribed to the topic, holds its partitions and then stops
     * polling, so that it blocks the next rebalance of the group until its max.poll.interval.ms has expired.
     */
    KafkaConsumer<byte[], byte[]> createStuckGroupMember(String groupId, String topic, Duration maxPollInterval) {
        Map<String, Object> props = consumerProps();
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, (int) maxPollInterval.toMillis());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
        KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(topic));
        long deadline = System.currentTimeMillis() + 30_000;
        while (consumer.assignment().isEmpty() && System.currentTimeMillis() < deadline) {
            consumer.poll(Duration.ofMillis(200));
        }
        if (consumer.assignment().isEmpty()) {
            consumer.close();
            throw new IllegalStateException("Stuck group member did not get an assignment");
        }
        return consumer;
    }

    /**
     * Starts a consumer of the given group that keeps polling the topic without committing, simulating a concurrent
     * run. Its client id sorts before the default client ids, so that the range assignor assigns it the partitions.
     */
    AutoCloseable startActiveGroupMember(String groupId, String topic) {
        Map<String, Object> props = consumerProps();
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, "a-active-member-" + UUID.randomUUID());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        AtomicBoolean running = new AtomicBoolean(true);
        CountDownLatch assigned = new CountDownLatch(1);
        Thread thread = new Thread(() -> {
            try (KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(props)) {
                consumer.subscribe(List.of(topic));
                while (running.get()) {
                    consumer.poll(Duration.ofMillis(100));
                    consumer.pause(consumer.assignment());
                    if (!consumer.assignment().isEmpty()) {
                        assigned.countDown();
                    }
                }
            }
        });
        thread.start();
        try {
            if (!assigned.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Active group member did not get an assignment");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return () -> {
            running.set(false);
            thread.join(30_000);
        };
    }

    private List<TopicPartition> partitions(KafkaConsumer<byte[], byte[]> consumer, String topic) {
        List<PartitionInfo> infos = consumer.partitionsFor(topic);
        return infos.stream().map(info -> new TopicPartition(topic, info.partition())).toList();
    }

    private KafkaConsumer<byte[], byte[]> createConsumer() {
        Map<String, Object> props = consumerProps();
        props.remove(ConsumerConfig.GROUP_ID_CONFIG);
        return new KafkaConsumer<>(props);
    }

    private Map<String, Object> consumerProps() {
        Map<String, Object> props = new HashMap<>(kafkaConfiguration.consumerConfig(KafkaProperties.DEFAULT_CLUSTER));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.remove(ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG);
        return props;
    }

    private KafkaProducer<byte[], byte[]> createProducer() {
        Map<String, Object> props = new HashMap<>(kafkaConfiguration.producerConfig(KafkaProperties.DEFAULT_CLUSTER));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        props.remove(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG);
        return new KafkaProducer<>(props);
    }
}
