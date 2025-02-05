package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

@Slf4j
@Component
public class KafkaBatchConsumerProducer {

    @Value("${jeap.messaging.kafka.consumer.bootstrap-servers}")
    private String consumerBootstrapServers;

    @Value("${jeap.messaging.kafka.consumer.group-id}")
    private String consumerGroupId;

    @Value("${jeap.messaging.kafka.consumer.key-deserializer}")
    private String consumerKeyDeserializer;

    @Value("${jeap.messaging.kafka.consumer.value-deserializer}")
    private String consumerValueDeserializer;

    @Value("${jeap.messaging.kafka.consumer.auto-offset-reset}")
    private String consumerAutoOffsetReset;

    @Value("${jeap.messaging.kafka.producer.bootstrap-servers}")
    private String producerBootstrapServers;

    @Value("${jeap.messaging.kafka.producer.key-serializer}")
    private String producerKeySerializer;

    @Value("${jeap.messaging.kafka.producer.value-serializer}")
    private String producerValueSerializer;

    @Value("${jeap.errorhandling.deadLetterTopicName}")
    private String deadLetterTopicName;

    @Value("${jeap.errorhandling.topic}")
    private String targetTopic;

    public void consumeAndProduce(int maxRecords) {
        Properties consumerProps = getConsumerProps();
        Properties producerProps = getProducerProps();

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
             KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {

            consumer.subscribe(Collections.singletonList(deadLetterTopicName));
            int received = 0;

            while (received < maxRecords) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, String> record : records) {
                    log.info("Received from dead-letter queue: {}", record.value());

                    producer.send(new ProducerRecord<>(targetTopic, record.key(), record.value()));
                    log.info("Produced to target topic: {}", record.value());

                    received++;
                    if (received >= maxRecords) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error occurred while consuming and producing messages", e);
        }

        log.info("Consumed and produced {} records. Stopping consumer and producer.", maxRecords);
    }

    private @NotNull Properties getConsumerProps() {
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, consumerBootstrapServers);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, consumerKeyDeserializer);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, consumerValueDeserializer);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, consumerAutoOffsetReset);
        return consumerProps;
    }

    private @NotNull Properties getProducerProps() {
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, producerBootstrapServers);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, producerKeySerializer);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, producerValueSerializer);
        return producerProps;
    }
}
