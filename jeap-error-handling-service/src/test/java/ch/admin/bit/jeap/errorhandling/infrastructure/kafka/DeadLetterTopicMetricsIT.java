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
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.messaging.kafka.serde.confluent.CustomKafkaAvroSerializer;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class DeadLetterTopicMetricsIT extends ErrorHandlingITBase {

    @Autowired
    private DeadLetterTopicMetrics deadLetterTopicMetrics;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void refresh_messagesPendingOnDeadLetterTopic_countsPendingMessagesAndReflectsReactivation() {
        // baseline instead of an assumed zero: other tests sharing the embedded broker may have left pending
        // messages or committed progress on the dead letter topic
        long baseline = refreshAndGetPendingCount();

        sendSync(DEAD_LETTER_TOPIC, createMessageProcessingFailedEvent());
        sendSync(DEAD_LETTER_TOPIC, createMessageProcessingFailedEvent());

        assertThat(refreshAndGetPendingCount()).isEqualTo(baseline + 2);
        assertThat(meterRegistry.get(DeadLetterTopicMetrics.PENDING_MESSAGES_GAUGE_METRIC).gauge().value())
                .isEqualTo(baseline + 2);

        // reactivating the pending messages triggers an immediate refresh, without waiting for the next
        // scheduled run
        kafkaDeadLetterBatchConsumerProducer.consumeAndProduce(2);

        await("the pending message count reflects the reactivation").atMost(FORTY_SECONDS)
                .until(() -> deadLetterTopicMetrics.getPendingMessageCount() == baseline);
        assertThat(meterRegistry.get(DeadLetterTopicMetrics.PENDING_MESSAGES_GAUGE_METRIC).gauge().value())
                .isEqualTo(baseline);
    }

    private long refreshAndGetPendingCount() {
        deadLetterTopicMetrics.refresh();
        return deadLetterTopicMetrics.getPendingMessageCount();
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
}
