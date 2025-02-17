package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventIdentity;
import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventPublisher;
import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventType;
import ch.admin.bit.jeap.errorhandling.ErrorHandlingITBase;
import ch.admin.bit.jeap.errorhandling.event.test.TestEvent;
import ch.admin.bit.jeap.errorhandling.event.test.TestPayload;
import ch.admin.bit.jeap.errorhandling.event.test.TestReferences;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageHandlerException;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageHandlerExceptionInformation;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedEvent;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedEventBuilder;
import ch.admin.bit.jeap.messaging.kafka.KafkaConfiguration;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.messaging.kafka.serde.confluent.CustomKafkaAvroSerializer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class KafkaDeadLetterBatchConsumerProducerIT extends ErrorHandlingITBase {

    @Autowired
    private KafkaDeadLetterBatchConsumerProducer kafkaDeadLetterBatchConsumerProducer;

    @Autowired
    private KafkaConfiguration kafkaConfiguration;

    @Test
    void consumeAndProduce_fetchOneMessage_oneMessageResent() {
        //given
        sendSync(DEAD_LETTER_TOPIC, createEventProcessingFailedEvent());
        sendSync(DEAD_LETTER_TOPIC, createEventProcessingFailedEvent());
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
        sendSync(DEAD_LETTER_TOPIC, createEventProcessingFailedEvent());
        sendSync(DEAD_LETTER_TOPIC, createEventProcessingFailedEvent());
        sendSync(DEAD_LETTER_TOPIC, createEventProcessingFailedEvent());
        assertThat(errorRepository.findAll()).isEmpty();

        //when
        kafkaDeadLetterBatchConsumerProducer.consumeAndProduce(3);

        //then
        awaitSingleErrorInRepository(3);
    }

    @Test
    void consumeAndProduce_fetchMessagesAndWait_messagesResent() {
        //given
        sendSync(DEAD_LETTER_TOPIC, createEventProcessingFailedEvent());
        sendSync(DEAD_LETTER_TOPIC, createEventProcessingFailedEvent());
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

    @BeforeEach
    void cleanUp() {
        errorRepository.deleteAll();
        causingEventRepository.deleteAll();
    }

    private MessageProcessingFailedEvent createEventProcessingFailedEvent() {
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
                    .originalMessage(originalMessage)
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

}
