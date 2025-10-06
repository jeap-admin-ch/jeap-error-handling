package ch.admin.bit.jeap.errorhandling;

import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventIdentity;
import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventPublisher;
import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventType;
import ch.admin.bit.jeap.errorhandling.domain.eventHandler.ErrorEventHandlerService;
import ch.admin.bit.jeap.errorhandling.event.test.TestEvent;
import ch.admin.bit.jeap.errorhandling.event.test.TestPayload;
import ch.admin.bit.jeap.errorhandling.event.test.TestReferences;
import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.ErrorEventHandler;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorEventData.Temporality;
import ch.admin.bit.jeap.messaging.avro.*;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageHandlerExceptionInformation;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedEvent;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedEventBuilder;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.messaging.kafka.serde.confluent.CustomKafkaAvroSerializer;
import ch.admin.bit.jeap.messaging.kafka.test.TestKafkaListener;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;


@ActiveProfiles("temporary-retry-it")
@SpringBootTest( properties = {
                "jeap.errorhandling.deadLetterTopicName=" + ErrorHandlingITBase.DEAD_LETTER_TOPIC,
                "jeap.errorhandling.topic=" + ErrorHandlingITBase.ERROR_TOPIC,
                "jeap.errorhandling.kafka.errorhandling.retry-interval=2s",
                "logging.level.ch.admin.bit.jeap.errorhandling=DEBUG"})
@DirtiesContext
class ErrorHandlingTemporaryRetryIT extends ErrorHandlingITBase {

    @Autowired
    private TestErrorEventHandler testErrorEventHandler;

    @Autowired
    private DltConsumer dltConsumer;

    private static TestEvent createTestEvent(String messagePayload) {
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
                        .setMessage(messagePayload)
                        .build())
                .setPublisher(AvroDomainEventPublisher.newBuilder()
                        .setSystem("TEST")
                        .setService("test-service")
                        .build())
                .build();
    }

    private MessageProcessingFailedEvent createMessageProcessingFailedEvent(AvroMessage avroMessage) {
        CustomKafkaAvroSerializer avroSerializer = new CustomKafkaAvroSerializer();
        avroSerializer.configure(kafkaConfiguration.consumerConfig(KafkaProperties.DEFAULT_CLUSTER), false);
        avroMessage.setSerializedMessage(avroSerializer.serialize("Topic", avroMessage));
        ConsumerRecord<?, ?> originalMessage = new ConsumerRecord<>("Topic", 1, 1, null, avroMessage);

        TestMessageProcessingException eventHandleException = new TestMessageProcessingException(MessageHandlerExceptionInformation.Temporality.PERMANENT, "500", "Payload");
        return MessageProcessingFailedEventBuilder.create()
                .eventHandleException(eventHandleException)
                .serviceName("service")
                .systemName("system")
                .originalMessage(originalMessage, avroMessage)
                .stackTraceHash("test-stack-trace-hash")
                .build();
    }

    @Test
    void testRecoverableMessageProcessingFailedEventHandlingExceptionsAreRetriedUntilSuccessful() {
        // given
        final TestEvent testEvent = createTestEvent("unexpected error");
        final String testEventIdempotenceId = testEvent.getIdentity().getIdempotenceId();
        final MessageProcessingFailedEvent errorEvent = createMessageProcessingFailedEvent(testEvent);
        assertNotNull(errorEvent.getPayload().getFailedMessageMetadata());
        // fail the handling of this event in the EHS 5 times with an exception that classifies as recoverable
        testErrorEventHandler.failMessageHandlingRecoverablyTimes(testEventIdempotenceId, 5);

        // when
        kafkaTemplate.send(ERROR_TOPIC, errorEvent);

        // then
        Error error = awaitErrorPersistedForFailingEventWithIdempotenceId(testEventIdempotenceId);
        assertSame(Temporality.PERMANENT, error.getErrorEventData().getTemporality());
        assertEquals(errorEvent.getIdentity().getEventId(), error.getErrorEventMetadata().getId());
        assertEquals(testEvent.getIdentity().getId(), error.getCausingEventMetadata().getId());
        // expecting 5 failed message handling calls that were retried plus the handling that succeeded -> 5 + 1 = 6
        assertEquals(6, testErrorEventHandler.getMessageHandlingCountByIdempotenceId(testEventIdempotenceId));
    }

    @Test
    void testFatalMessageProcessingFailedEventHandlingExceptionsAreNotRetriedAndSentToTheDeadLetterTopic() {
        // given
        final TestEvent testEvent = createTestEvent("unexpected error");
        final String testEventIdempotenceId = testEvent.getIdentity().getIdempotenceId();
        final MessageProcessingFailedEvent errorEvent = createMessageProcessingFailedEvent(testEvent);
        assertNotNull(errorEvent.getPayload().getFailedMessageMetadata());
        // fail the handling of this event in the EHS immediately with an exception that classifies as fatal
        testErrorEventHandler.failMessageHandlingFatally(testEventIdempotenceId);

        // when
        kafkaTemplate.send(ERROR_TOPIC, errorEvent);

        // then
        awaitMessageWithIdempotenceIdInDlt(testEventIdempotenceId);
        // expecting exactly one failed message handling call (no retry because of the fatal exception)
        assertEquals(1, testErrorEventHandler.getMessageHandlingCountByIdempotenceId(testEventIdempotenceId));
    }

    private Error awaitErrorPersistedForFailingEventWithIdempotenceId(String idempotenceId) {
        await("error has been persisted in repository").atMost(FORTY_SECONDS)
                .until(() -> errorRepository.findAll().stream()
                        .anyMatch(error -> error.getCausingEventMetadata().getIdempotenceId().equals(idempotenceId)));
        return assertAndGetSingleError(idempotenceId);
    }

    private void awaitMessageWithIdempotenceIdInDlt(String idempotenceId) {
        await("message has been delivered to the dead letter topic").atMost(FORTY_SECONDS)
                .until(() -> dltConsumer.hasMessageWithIdempotenceId(idempotenceId));
    }

    private Error assertAndGetSingleError(String idempotenceId) {
        List<Error> matchingErrors = errorRepository.findAll().stream()
                .filter(error -> error.getCausingEventMetadata().getIdempotenceId().equals(idempotenceId))
                .toList();
        assertEquals(1, matchingErrors.size());
        return matchingErrors.getFirst();
    }

    @Profile("temporary-retry-it")
    @TestConfiguration
    static class TestSpecificConfiguration {
        @Bean
        @Primary
        ErrorEventHandler errorEventHandler(ErrorEventHandlerService eventHandlerService) {
            return new TestErrorEventHandler(eventHandlerService);
        }
        @Bean
        DltConsumer dltConsumer() {
            return new DltConsumer();
        }
    }

    @Slf4j
    @RequiredArgsConstructor
    private static class TestErrorEventHandler implements ErrorEventHandler {

        private final ErrorEventHandlerService eventHandlerService;
        private final Map<String, Integer> messageHandlingCountByIdempotenceId = new HashMap<>();
        private final Set<String> failMessageHandlingFatallyForIdempotenceIds = new CopyOnWriteArraySet<>();
        private final Map<String, Integer> failMessageHandlingTemporaryTimesByIdempotenceId = new ConcurrentHashMap<>();

        public void failMessageHandlingFatally(String idempotenceId) {
            failMessageHandlingFatallyForIdempotenceIds.add(idempotenceId);
        }

        public void failMessageHandlingRecoverablyTimes(String idempotenceId, int times) {
            failMessageHandlingTemporaryTimesByIdempotenceId.put(idempotenceId, times);
        }

        public int getMessageHandlingCountByIdempotenceId(String idempotenceId) {
            return messageHandlingCountByIdempotenceId.getOrDefault(idempotenceId, 0);
        }

        @Override
        public void handle(String clusterName, MessageProcessingFailedEvent errorEvent) {
            synchronized (this) {
                String failedMessageIdempotenceId = errorEvent.getPayload().getFailedMessageMetadata().getIdempotenceId();
                Integer handlingCounter = messageHandlingCountByIdempotenceId.getOrDefault(failedMessageIdempotenceId,0);
                log.info("Handling count for failed message idempotence id '{}' is {}.", failedMessageIdempotenceId, handlingCounter);
                Integer thisTry = handlingCounter + 1;
                messageHandlingCountByIdempotenceId.put(failedMessageIdempotenceId, thisTry);
                if (failMessageHandlingFatallyForIdempotenceIds.contains(failedMessageIdempotenceId)) {
                    log.info("Throwing fatal test exception for failed message idempotence id '{}'.", failedMessageIdempotenceId);
                    throw new IllegalStateException("Fatal test exception");
                }
                int failTimes = failMessageHandlingTemporaryTimesByIdempotenceId.getOrDefault(failedMessageIdempotenceId, 0);
                if (handlingCounter < failTimes) {
                    String tryInfo = "try number " + thisTry + " of " + failTimes;
                    log.info("Throwing recoverable test exception for failed message idempotence id '{}' with {}.", failedMessageIdempotenceId, tryInfo);
                    throw new DataAccessResourceFailureException("Recoverable test exception: " + tryInfo);
                }
                log.info("Handling error event for failed message idempotence id '{}' at try {}.", failedMessageIdempotenceId, thisTry);
                eventHandlerService.handle(clusterName, errorEvent);
            }
        }
    }

    @Slf4j
    @Getter
    static class DltConsumer {

        private final List<MessageProcessingFailedEvent> consumedMessages = new ArrayList<>();

        @TestKafkaListener(topics = {ErrorHandlingITBase.ERROR_TOPIC}, groupId = "temporary-retry-it-dlt-consumer")
        public void consume(MessageProcessingFailedEvent message) {
            consumedMessages.add(message);
            log.info("DLT consumer received the message: {}", message);
        }

        boolean hasMessageWithIdempotenceId(String idempotenceId) {
            return consumedMessages.stream()
                    .anyMatch(message -> message.getPayload().getFailedMessageMetadata().getIdempotenceId().equals(idempotenceId));
        }

    }

}
