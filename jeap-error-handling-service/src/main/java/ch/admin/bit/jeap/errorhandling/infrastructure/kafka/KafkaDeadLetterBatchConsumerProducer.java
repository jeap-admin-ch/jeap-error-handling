package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.messaging.kafka.KafkaConfiguration;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.messaging.kafka.spring.JeapKafkaPropertyFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.MAX_POLL_RECORDS_CONFIG;

@Slf4j
@Component
public class KafkaDeadLetterBatchConsumerProducer {

    private final String deadLetterTopicName;
    private final String errorTopicName;
    private final KafkaConfiguration kafkaConfiguration;
    private final String defaultClusterName;

    public KafkaDeadLetterBatchConsumerProducer(@Value("${jeap.errorhandling.deadLetterTopicName}") String deadLetterTopicName,
                                                @Value("${jeap.errorhandling.topic}") String errorTopicName,
                                                KafkaConfiguration kafkaConfiguration,
                                                Environment environment) {
        this.deadLetterTopicName = deadLetterTopicName;
        this.errorTopicName = errorTopicName;
        this.kafkaConfiguration = kafkaConfiguration;
        KafkaProperties kafkaProperties = JeapKafkaPropertyFactory.createJeapKafkaProperties(environment);
        this.defaultClusterName = kafkaProperties.getDefaultClusterName();
    }

    @Async
    public void consumeAndProduce(int maxRecords) {
        log.info("Start consuming and producing messages from topic {} to topic {} with maxRecords {}", deadLetterTopicName, errorTopicName, maxRecords);

        int totalRecordsProcessed = 0;
        int remaining = maxRecords;
        while (remaining > 0) {
            int batchSize = Math.min(remaining, 50);
            int processed = consumeAndProduceInBatch(batchSize);
            totalRecordsProcessed += processed;
            if (processed == 0) {
                break;
            }
            remaining -= processed;
        }

        log.info("Consumed and produced {} records. Stopping consumer and producer.", totalRecordsProcessed);
    }

    private int consumeAndProduceInBatch(int maxRecords) {
        log.info("Poll topic with maxRecords {} from {}, sending to {}", maxRecords, deadLetterTopicName, errorTopicName);

        try (KafkaConsumer<byte[], byte[]> consumer = createConsumer(kafkaConfiguration, maxRecords);
             KafkaProducer<byte[], byte[]> producer = createProducer(kafkaConfiguration)) {

            consumer.subscribe(Collections.singletonList(deadLetterTopicName));

            ConsumerRecords<byte[], byte[]> records = consumer.poll(Duration.ofSeconds(5));

            final int received = records.count();
            log.info("Received {} records", received);

            for (ConsumerRecord<byte[], byte[]> data : records) {
                log.debug("Received message from partition {} with offset {} sending to {}", data.partition(), data.offset(), errorTopicName);

                ProducerRecord<byte[], byte[]> producerRecord =
                        (data.key() != null)
                                ? new ProducerRecord<>(errorTopicName, data.key(), data.value())
                                : new ProducerRecord<>(errorTopicName, data.value());

                data.headers().forEach(header -> producerRecord.headers().add(header));
                producer.send(producerRecord);
            }

            producer.flush();
            consumer.commitSync();
            log.info("Consumed and produced {} records.", received);
            return received;

        } catch (Exception e) {
            log.error("Error occurred while consuming and producing messages", e);
            throw new IllegalStateException("Error occurred while consuming and producing messages", e);
        }
    }

    private KafkaConsumer<byte[], byte[]> createConsumer(KafkaConfiguration kafkaConfiguration, int maxRecords) {
        Map<String, Object> props = new HashMap<>(kafkaConfiguration.consumerConfig(defaultClusterName));

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);

        props.put(MAX_POLL_RECORDS_CONFIG, maxRecords);
        props.put(AUTO_OFFSET_RESET_CONFIG, "earliest");
        // Disable auto-commit so offsets are only committed after all records are successfully
        // produced, via the explicit commitSync() in consumeAndProduceInBatch.
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return new KafkaConsumer<>(props);
    }

    private KafkaProducer<byte[], byte[]> createProducer(KafkaConfiguration kafkaConfiguration) {
        Map<String, Object> props = new HashMap<>(kafkaConfiguration.producerConfig(defaultClusterName));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        props.remove(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG);

        return new KafkaProducer<>(props);
    }
}
