package ch.admin.bit.jeap.errorhandling;

import ch.admin.bit.jeap.messaging.kafka.KafkaConfiguration;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.messaging.kafka.spring.JeapKafkaPropertyFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test-only relay that continuously copies records from the dead letter topic to the topic the error handling
 * service consumes from.
 * <p>
 * In production the two topics belong to different applications: the message consumers of a system publish
 * their failures to the error topic ({@code jeap.messaging.kafka.errorTopicName}), the error handling service
 * consumes that topic ({@code jeap.errorhandling.topic}) and publishes its own failures to the dead letter
 * topic ({@code jeap.errorhandling.deadLetterTopicName}). In an integration test the failing test consumer
 * shares the application context - and therefore the error topic, which
 * {@code DeadLetterTopicNameEnvPostProcessor} pins to the dead letter topic - with the error handling service.
 * This relay bridges that gap: it takes the failures the test consumer produced to the dead letter topic and
 * hands them to the error handling service on its input topic, as a separate application would.
 * <p>
 * Records are copied at byte level including their headers, exactly like
 * {@link ch.admin.bit.jeap.errorhandling.infrastructure.kafka.KafkaDeadLetterBatchConsumerProducer} does for
 * the dead letter reactivation. A marker header prevents relaying a record twice, so a message the error
 * handling service itself dead-letters cannot end up in an endless relay loop.
 * <p>
 * Activated with the profile {@value #PROFILE}.
 */
@Slf4j
@Component
@Profile(DeadLetterToErrorTopicRelay.PROFILE)
class DeadLetterToErrorTopicRelay {

    static final String PROFILE = "dead-letter-relay";

    private static final String RELAYED_HEADER_NAME = "test-dead-letter-relayed";
    private static final Duration POLL_TIMEOUT = Duration.ofMillis(500);

    private final String deadLetterTopicName;
    private final String errorTopicName;
    private final KafkaConfiguration kafkaConfiguration;
    private final String defaultClusterName;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private volatile KafkaConsumer<byte[], byte[]> consumer;
    private Thread relayThread;

    DeadLetterToErrorTopicRelay(@Value("${jeap.errorhandling.deadLetterTopicName}") String deadLetterTopicName,
                                @Value("${jeap.errorhandling.topic}") String errorTopicName,
                                KafkaConfiguration kafkaConfiguration,
                                Environment environment) {
        this.deadLetterTopicName = deadLetterTopicName;
        this.errorTopicName = errorTopicName;
        this.kafkaConfiguration = kafkaConfiguration;
        KafkaProperties kafkaProperties = JeapKafkaPropertyFactory.createJeapKafkaProperties(environment);
        this.defaultClusterName = kafkaProperties.getDefaultClusterName();
    }

    @PostConstruct
    void start() {
        log.info("Starting dead letter relay from topic {} to topic {}", deadLetterTopicName, errorTopicName);
        relayThread = new Thread(this::relay, "dead-letter-relay");
        relayThread.setDaemon(true);
        relayThread.start();
    }

    @PreDestroy
    void stop() throws InterruptedException {
        running.set(false);
        KafkaConsumer<byte[], byte[]> currentConsumer = consumer;
        if (currentConsumer != null) {
            currentConsumer.wakeup();
        }
        if (relayThread != null) {
            relayThread.join(Duration.ofSeconds(10).toMillis());
        }
        log.info("Stopped dead letter relay");
    }

    private void relay() {
        try (KafkaConsumer<byte[], byte[]> kafkaConsumer = createConsumer();
             KafkaProducer<byte[], byte[]> producer = createProducer()) {
            consumer = kafkaConsumer;
            kafkaConsumer.subscribe(Collections.singletonList(deadLetterTopicName));
            while (running.get()) {
                ConsumerRecords<byte[], byte[]> records = kafkaConsumer.poll(POLL_TIMEOUT);
                if (!records.isEmpty()) {
                    relayRecords(records, producer);
                    producer.flush();
                    kafkaConsumer.commitSync();
                }
            }
        } catch (WakeupException e) {
            log.debug("Dead letter relay woken up for shutdown");
        } catch (Exception e) {
            log.error("Dead letter relay failed", e);
        } finally {
            consumer = null;
        }
    }

    private void relayRecords(ConsumerRecords<byte[], byte[]> records, KafkaProducer<byte[], byte[]> producer) {
        for (ConsumerRecord<byte[], byte[]> data : records) {
            if (data.headers().lastHeader(RELAYED_HEADER_NAME) != null) {
                log.warn("Not relaying an already relayed record from partition {} with offset {} again", data.partition(), data.offset());
                continue;
            }
            log.info("Relaying record from partition {} with offset {} to topic {}", data.partition(), data.offset(), errorTopicName);
            ProducerRecord<byte[], byte[]> producerRecord = (data.key() != null)
                    ? new ProducerRecord<>(errorTopicName, data.key(), data.value())
                    : new ProducerRecord<>(errorTopicName, data.value());
            data.headers().forEach(header -> producerRecord.headers().add(header));
            producerRecord.headers().add(RELAYED_HEADER_NAME, "true".getBytes(StandardCharsets.UTF_8));
            producer.send(producerRecord);
        }
    }

    private KafkaConsumer<byte[], byte[]> createConsumer() {
        Map<String, Object> props = new HashMap<>(kafkaConfiguration.consumerConfig(defaultClusterName));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dead-letter-relay");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
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
