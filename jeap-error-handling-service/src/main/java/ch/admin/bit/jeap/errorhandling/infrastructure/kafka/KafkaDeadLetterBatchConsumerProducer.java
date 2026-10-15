package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.messaging.kafka.KafkaConfiguration;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.messaging.kafka.spring.JeapKafkaPropertyFactory;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.MAX_POLL_RECORDS_CONFIG;

/**
 * Reactivates messages from the dead letter topic by forwarding them to the error topic, see
 * {@link DeadLetterReactivationRun}.
 */
@Component
public class KafkaDeadLetterBatchConsumerProducer {

    static final String REACTIVATED_MESSAGES_COUNTER_METRIC = "eh_deadletter_reactivated_messages";
    static final String REACTIVATION_RUNS_TIMER_METRIC = "eh_deadletter_reactivation_runs";
    static final String OUTCOME_TAG = "outcome";

    private final String deadLetterTopicName;
    private final String errorTopicName;
    private final KafkaConfiguration kafkaConfiguration;
    private final DeadLetterReactivationProperties properties;
    private final MeterRegistry meterRegistry;
    private final String defaultClusterName;
    private final Counter reactivatedMessagesCounter;

    public KafkaDeadLetterBatchConsumerProducer(@Value("${jeap.errorhandling.deadLetterTopicName}") String deadLetterTopicName,
                                                @Value("${jeap.errorhandling.topic}") String errorTopicName,
                                                KafkaConfiguration kafkaConfiguration,
                                                DeadLetterReactivationProperties properties,
                                                MeterRegistry meterRegistry,
                                                Environment environment) {
        this.deadLetterTopicName = deadLetterTopicName;
        this.errorTopicName = errorTopicName;
        this.kafkaConfiguration = kafkaConfiguration;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        KafkaProperties kafkaProperties = JeapKafkaPropertyFactory.createJeapKafkaProperties(environment);
        this.defaultClusterName = kafkaProperties.getDefaultClusterName();
        this.reactivatedMessagesCounter = Counter.builder(REACTIVATED_MESSAGES_COUNTER_METRIC)
                .description("Messages forwarded from the dead letter topic to the error topic and acknowledged by the error topic")
                .register(meterRegistry);
    }

    @Async
    public void consumeAndProduce(int maxRecords) {
        reactivate(deadLetterTopicName, errorTopicName, maxRecords, Map.of(), properties.getIdleTimeout());
    }

    /**
     * Executes one reactivation run synchronously. Source and target topic as well as consumer configuration
     * overrides can be specified for tests.
     */
    public DeadLetterReactivationRun.Result reactivate(String sourceTopic, String targetTopic, int maxRecords,
                                                       Map<String, Object> consumerConfigOverrides, Duration idleTimeout) {
        DeadLetterReactivationRun.Result result = null;
        long startNanos = System.nanoTime();
        try (KafkaConsumer<byte[], byte[]> consumer = createConsumer(consumerConfigOverrides);
             KafkaProducer<byte[], byte[]> producer = createProducer()) {
            result = new DeadLetterReactivationRun(consumer, producer, sourceTopic, targetTopic, maxRecords,
                    idleTimeout, reactivatedMessagesCounter::increment).execute();
            return result;
        } finally {
            String outcome = result == null ? DeadLetterReactivationRun.EndReason.ERROR.tagValue() : result.endReason().tagValue();
            Timer.builder(REACTIVATION_RUNS_TIMER_METRIC)
                    .description("Duration of dead letter reactivation runs, tagged with the reason the run ended")
                    .tag(OUTCOME_TAG, outcome)
                    .register(meterRegistry)
                    .record(Duration.ofNanos(System.nanoTime() - startNanos));
        }
    }

    private KafkaConsumer<byte[], byte[]> createConsumer(Map<String, Object> overrides) {
        Map<String, Object> props = new HashMap<>(kafkaConfiguration.consumerConfig(defaultClusterName));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(MAX_POLL_RECORDS_CONFIG, properties.getMaxPollRecords());
        props.put(AUTO_OFFSET_RESET_CONFIG, "earliest");
        // Offsets are committed explicitly once the forwarded messages have been acknowledged by the error topic
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.putAll(overrides);
        return new KafkaConsumer<>(props);
    }

    private KafkaProducer<byte[], byte[]> createProducer() {
        Map<String, Object> props = new HashMap<>(kafkaConfiguration.producerConfig(defaultClusterName));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        props.remove(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG);
        return new KafkaProducer<>(props);
    }
}
