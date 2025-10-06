package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventIdentity;
import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventPublisher;
import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventType;
import ch.admin.bit.jeap.errorhandling.ErrorHandlingITBase;
import ch.admin.bit.jeap.errorhandling.event.test.TestEvent;
import ch.admin.bit.jeap.errorhandling.event.test.TestPayload;
import ch.admin.bit.jeap.errorhandling.event.test.TestReferences;
import ch.admin.bit.jeap.messaging.avro.AvroMessage;
import ch.admin.bit.jeap.messaging.avro.AvroMessageKey;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageHandlerException;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageHandlerExceptionInformation;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedEvent;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedEventBuilder;
import ch.admin.bit.jeap.messaging.kafka.crypto.JeapKafkaAvroSerdeCryptoConfig;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.messaging.kafka.serde.confluent.CustomKafkaAvroSerializer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class KafkaDeadLetterBatchConsumerProducerIT extends ErrorHandlingITBase {

    @Test
    void consumeAndProduce_fetchOneMessage_oneMessageResent() {
        //given
        sendSync(DEAD_LETTER_TOPIC, createMessageProcessingFailedEvent());
        sendSync(DEAD_LETTER_TOPIC, createMessageProcessingFailedEvent());
        assertThat(errorRepository.findAll()).isEmpty();

        //when
        kafkaDeadLetterBatchConsumerProducer.consumeAndProduce(1);

        //then
        awaitSingleErrorInRepository(1);

        //when
        kafkaDeadLetterBatchConsumerProducer.consumeAndProduce(1);

        //then
        awaitSingleErrorInRepository(2);
    }

    @Test
    void consumeAndProduce_fetchMessages_messagesResent() {
        //given
        sendSync(DEAD_LETTER_TOPIC, createMessageProcessingFailedEvent());
        sendSync(DEAD_LETTER_TOPIC, createMessageProcessingFailedEvent());
        sendSync(DEAD_LETTER_TOPIC, createMessageProcessingFailedEvent());
        assertThat(errorRepository.findAll()).isEmpty();

        //when
        kafkaDeadLetterBatchConsumerProducer.consumeAndProduce(3);

        //then
        awaitSingleErrorInRepository(3);
    }

    @Test
    void consumeAndProduce_fetchMessagesAndWait_messagesResent() {
        //given
        sendSync(DEAD_LETTER_TOPIC, createMessageProcessingFailedEvent());
        sendSync(DEAD_LETTER_TOPIC, createMessageProcessingFailedEvent());
        assertThat(errorRepository.findAll()).isEmpty();

        //when
        kafkaDeadLetterBatchConsumerProducer.consumeAndProduce(3);

        //then
        awaitSingleErrorInRepository(2);
    }

    private void awaitSingleErrorInRepository(int size) {
        await("failure has been recorded in repository").atMost(FORTY_SECONDS)
                .until(() -> errorRepository.findAll().size() == size);
    }



    private MessageProcessingFailedEvent createMessageProcessingFailedEvent() {
        try (CustomKafkaAvroSerializer avroSerializer = new CustomKafkaAvroSerializer()) {
            avroSerializer.configure(kafkaConfiguration.consumerConfig(KafkaProperties.DEFAULT_CLUSTER), false);
            TestEvent domainEvent = createTestEvent();
            domainEvent.setSerializedMessage(avroSerializer.serialize("Topic", domainEvent));
            ConsumerRecord<?, ?> originalMessage = new ConsumerRecord<>("Topic", 1, 1, null, domainEvent);

            MessageHandlerException eventHandleException = MessageHandlerException.builder()
                    .description("description")
                    .errorCode(MessageHandlerExceptionInformation.StandardErrorCodes.UNKNOWN_EXCEPTION.name())
                    .temporality(MessageHandlerExceptionInformation.Temporality.UNKNOWN)
                    .build();

            return MessageProcessingFailedEventBuilder.create()
                    .eventHandleException(eventHandleException)
                    .serviceName("service")
                    .systemName("system")
                    .originalMessage(originalMessage, null)
                    .build();
        }
    }

    private static TestEvent createTestEvent() {
        return TestEvent.newBuilder()
                .setType(AvroDomainEventType.newBuilder()
                        .setName("TestEvent")
                        .setVersion("1")
                        .build())
                .setReferences(TestReferences.newBuilder().build())
                .setDomainEventVersion("1.0.0")
                .setIdentity(AvroDomainEventIdentity.newBuilder()
                        .setEventId(UUID.randomUUID().toString())
                        .setIdempotenceId(UUID.randomUUID().toString())
                        .setCreated(Instant.now())
                        .build())
                .setPayload(TestPayload.newBuilder()
                        .setMessage("messagePayload")
                        .build())
                .setPublisher(AvroDomainEventPublisher.newBuilder()
                        .setSystem("TEST")
                        .setService("test-service")
                        .build())
                .build();
    }

    @Test
    void consumeAndProduce_shouldPreserveKafkaHeaders() {

        // given
        MessageProcessingFailedEvent event = createMessageProcessingFailedEvent();

        // Set header
        ProducerRecord<AvroMessageKey, AvroMessage> producerRecord =
                new ProducerRecord<>(DEAD_LETTER_TOPIC, event);
        String headerName = JeapKafkaAvroSerdeCryptoConfig.ENCRYPTED_VALUE_HEADER_NAME;
        byte[] headerValue = JeapKafkaAvroSerdeCryptoConfig.ENCRYPTED_VALUE_HEADER_TRUE;
        String headerNameCert = "jeap-cert";
        byte[] headerValueCert = {1, 2, 3};
        String headerNameSign = "jeap-sign";
        byte[] headerValueSign = {1, 2, 3, 4};
        String headerNameSignKey = "jeap-sign-key";
        byte[] headerValueSignKey = {1, 2, 3, 4, 5};

        producerRecord.headers().add(headerName, headerValue);
        producerRecord.headers().add(headerNameCert, headerValueCert);
        producerRecord.headers().add(headerNameSign, headerValueSign);
        producerRecord.headers().add(headerNameSignKey, headerValueSignKey);
        kafkaTemplate.send(producerRecord);

        assertThat(errorRepository.findAll()).isEmpty();

        // when
        kafkaDeadLetterBatchConsumerProducer.consumeAndProduce(1);

        // then
        awaitSingleErrorInRepository(1);

        List<MessageProcessingFailedEvent> messageProcessingFailedEvents = testConsumer.getConsumedMessageProcessingFailedEvents();
        assertThat(messageProcessingFailedEvents).hasSize(1);
        List<ConsumerRecord<AvroMessageKey, MessageProcessingFailedEvent>> consumedRecords = testConsumer.getConsumedMessageProcessingFailedEventRecords();
        assertThat(consumedRecords).hasSize(1);
        for (ConsumerRecord<AvroMessageKey, MessageProcessingFailedEvent> record : consumedRecords) {
            assertNotNull(getHeaderValue(JeapKafkaAvroSerdeCryptoConfig.ENCRYPTED_VALUE_HEADER_NAME, record), "Header value from original message is passed through");
            assertNotNull(getHeaderValue(headerNameCert, record), "Header value from original message is passed through");
            assertNotNull(getHeaderValue(headerNameSign, record), "Header value from original message is passed through");
            assertNotNull(getHeaderValue(headerNameSignKey, record), "Header value from original message is passed through");
        }
    }

    private Object getHeaderValue(String headerName, ConsumerRecord<AvroMessageKey, MessageProcessingFailedEvent> record) {
        if (record.headers() == null) {
            return null;
        }

        return record.headers().lastHeader(headerName);
    }
}
