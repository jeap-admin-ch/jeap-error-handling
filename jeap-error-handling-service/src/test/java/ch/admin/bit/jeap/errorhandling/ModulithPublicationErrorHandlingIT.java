package ch.admin.bit.jeap.errorhandling;

import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventIdentity;
import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventPublisher;
import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventType;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.AuditLog;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.CausingEvent;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error.ErrorState;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorEventData;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ModulithPublicationData;
import ch.admin.bit.jeap.messaging.kafka.test.TestKafkaListener;
import ch.admin.bit.jeap.modulith.command.discardpublication.DiscardModulithPublicationCommand;
import ch.admin.bit.jeap.modulith.command.retrypublication.RetryModulithPublicationCommand;
import ch.admin.bit.jeap.modulith.event.publicationprocessingfailed.ModulithPublicationProcessingFailedEvent;
import ch.admin.bit.jeap.modulith.event.publicationprocessingfailed.ModulithPublicationProcessingFailedPayload;
import ch.admin.bit.jeap.modulith.event.publicationprocessingfailed.ModulithPublicationProcessingFailedReferences;
import ch.admin.bit.jeap.modulith.event.publicationprocessingfailed.ModulithPublicationReference;
import ch.admin.bit.jeap.modulith.event.publicationprocessingfailed.Temporality;
import ch.admin.bit.jeap.security.resource.semanticAuthentication.SemanticApplicationRole;
import ch.admin.bit.jeap.security.resource.token.JeapAuthenticationContext;
import ch.admin.bit.jeap.security.test.jws.JwsBuilder;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.stereotype.Component;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static ch.admin.bit.jeap.errorhandling.ModulithPublicationErrorHandlingIT.DISCARD_COMMAND_TOPIC;
import static ch.admin.bit.jeap.errorhandling.ModulithPublicationErrorHandlingIT.MODULITH_FAILURE_TOPIC;
import static ch.admin.bit.jeap.errorhandling.ModulithPublicationErrorHandlingIT.RETRY_COMMAND_TOPIC;
import static ch.admin.bit.jeap.errorhandling.infrastructure.persistence.AuditLog.AuditedAction.DELETE_ERROR;
import static ch.admin.bit.jeap.errorhandling.infrastructure.persistence.AuditLog.AuditedAction.RESEND_CAUSING_EVENT;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

/**
 * End-to-end tests for the retry and discard workflow of failed Spring Modulith publications: a real
 * {@link ModulithPublicationProcessingFailedEvent} is consumed from the dedicated Modulith failure topic and the
 * resulting {@link RetryModulithPublicationCommand} / {@link DiscardModulithPublicationCommand} is asserted on the
 * command topic the failure event named, after it travelled through the transactional outbox.
 * <p>
 * Covers both temporalities: a permanent failure waits for a manual retry, a temporary one is retried automatically
 * by the resend scheduler and escalates to a permanent error once the retries are exhausted.
 */
@Slf4j
@ActiveProfiles(ModulithPublicationErrorHandlingIT.PROFILE)
@SpringBootTest(webEnvironment = DEFINED_PORT, properties = {
        "server.port=8306",
        "jeap.errorhandling.deadLetterTopicName=" + ErrorHandlingITBase.DEAD_LETTER_TOPIC,
        "jeap.errorhandling.topic=" + ErrorHandlingITBase.ERROR_TOPIC,
        "jeap.errorhandling.modulithPublicationProcessingFailedTopic=" + MODULITH_FAILURE_TOPIC,
        "jeap.security.oauth2.resourceserver.authorization-server.issuer=" + JwsBuilder.DEFAULT_ISSUER,
        "jeap.security.oauth2.resourceserver.authorization-server.jwk-set-uri=http://localhost:${server.port}/.well-known/jwks.json",
        // the manual task synchronization would advance the states this test asserts on, and it is not part of
        // what is tested here - see SyncWithManualTasksTest and TasksSynchronizeTest for its own coverage
        "jeap.errorhandling.task-management.synchronize.cron-expression=0 0 0 1 1 *",
        "logging.level.ch.admin.bit.jeap.errorhandling=DEBUG"})
@DirtiesContext
class ModulithPublicationErrorHandlingIT extends ErrorHandlingITBase {

    static final String PROFILE = "modulith-publication-it";
    static final String MODULITH_FAILURE_TOPIC = "modulith-publication-failure-topic";
    static final String RETRY_COMMAND_TOPIC = "modulith-retry-command-topic";
    static final String DISCARD_COMMAND_TOPIC = "modulith-discard-command-topic";

    private static final String SERIALIZED_EVENT = "{\"orderId\":\"4711\"}";
    private static final String SUBJECT = "69368608-D736-43C8-5F76-55B7BF168299";
    private static final JeapAuthenticationContext CONTEXT = JeapAuthenticationContext.SYS;

    private static final SemanticApplicationRole RETRY_ROLE = SemanticApplicationRole.builder()
            .system("jme").resource("error").operation("retry").build();
    private static final SemanticApplicationRole DELETE_ROLE = SemanticApplicationRole.builder()
            .system("jme").resource("error").operation("delete").build();

    private final RequestSpecification apiSpec;

    @Autowired
    private ModulithCommandConsumer commandConsumer;

    /**
     * The listener containers of the error handling service itself. They are registered as beans by
     * {@code KafkaErrorEventConsumerFactory} and are therefore not part of the {@code KafkaListenerEndpointRegistry}
     * holding the containers of the {@link TestKafkaListener} endpoints.
     */
    @Autowired
    private List<MessageListenerContainer> messageListenerContainers;

    ModulithPublicationErrorHandlingIT(@Value("${server.port}") int serverPort) {
        apiSpec = new RequestSpecBuilder().setPort(serverPort).build();
    }

    @BeforeEach
    void waitForKafkaListeners() {
        // Wait for every listener, not just for an arbitrary one: as long as a consumer has not been assigned its
        // partition, it misses the messages published to its topic, because it starts reading at the end of the topic.
        List<MessageListenerContainer> containers = new ArrayList<>(registry.getListenerContainers());
        containers.addAll(messageListenerContainers);
        containers.forEach(container -> ContainerTestUtils.waitForAssignment(container, 1));
        commandConsumer.reset();
    }

    @Test
    void permanentModulithFailure_manualRetry_sendsRetryCommand() {
        String publicationId = UUID.randomUUID().toString();
        ModulithPublicationProcessingFailedEvent failureEvent = publishModulithFailure(publicationId, 1, Temporality.PERMANENT);

        Error error = awaitSingleError();
        assertThat(error.getState()).isEqualTo(ErrorState.PERMANENT);
        assertThat(error.getErrorEventData().getTemporality()).isEqualTo(ErrorEventData.Temporality.PERMANENT);
        assertPublicationPersisted(error, publicationId);
        // a permanent failure is never retried on its own, it waits for someone to act on it
        assertThat(commandConsumer.retryCommands()).isEmpty();

        given().
                spec(apiSpec).
                auth().oauth2(createAuthTokenForUserRoles(RETRY_ROLE)).
                when().
                post("/api/error/{errorId}/event/retry", error.getId()).
                then().
                statusCode(HttpStatus.OK.value());

        RetryModulithPublicationCommand command = awaitRetryCommand(publicationId);
        assertThat(command.getIdentity().getIdempotenceId()).isEqualTo("retry:" + error.getId());
        assertThat(command.getReferences().getPublication().getFailureEventId())
                .isEqualTo(failureEvent.getIdentity().getEventId());
        // the manual task is closed by the task synchronization only after the outbox entry has been committed
        assertThat(reload(error).getState()).isEqualTo(ErrorState.RESOLVE_ON_MANUALTASK);
        assertAuditLog(error, RESEND_CAUSING_EVENT);
    }

    @Test
    void permanentModulithFailure_delete_sendsDiscardCommand() {
        String publicationId = UUID.randomUUID().toString();
        ModulithPublicationProcessingFailedEvent failureEvent = publishModulithFailure(publicationId, 1, Temporality.PERMANENT);

        Error error = awaitSingleError();
        assertThat(error.getState()).isEqualTo(ErrorState.PERMANENT);

        given().
                spec(apiSpec).
                auth().oauth2(createAuthTokenForUserRoles(DELETE_ROLE)).
                queryParam("reason", "publication is obsolete").
                when().
                delete("/api/error/{errorId}", error.getId()).
                then().
                statusCode(HttpStatus.OK.value());

        DiscardModulithPublicationCommand command = awaitDiscardCommand(publicationId);
        assertThat(command.getIdentity().getIdempotenceId()).isEqualTo("discard:" + error.getId());
        assertThat(command.getPayload().getReason()).isEqualTo("publication is obsolete");
        assertThat(command.getReferences().getPublication().getFailureEventId())
                .isEqualTo(failureEvent.getIdentity().getEventId());
        assertThat(reload(error).getState()).isEqualTo(ErrorState.DELETE_ON_MANUALTASK);
        assertThat(reload(error).getClosingReason()).isEqualTo("publication is obsolete");
        assertThat(commandConsumer.retryCommands()).isEmpty();
        assertAuditLog(error, DELETE_ERROR);
    }

    @Test
    void temporaryModulithFailure_isRetriedByResendScheduler() {
        String publicationId = UUID.randomUUID().toString();
        publishModulithFailure(publicationId, 1, Temporality.TEMPORARY);

        Error error = awaitSingleError();
        assertThat(error.getState()).isEqualTo(ErrorState.TEMPORARY_RETRY_PENDING);
        assertThat(error.getErrorEventData().getTemporality()).isEqualTo(ErrorEventData.Temporality.TEMPORARY);
        assertPublicationPersisted(error, publicationId);
        assertThat(scheduledResendRepository.findAll()).hasSize(1);

        // no user interaction: the resend scheduler picks the error up and asks the publishing application to
        // retry the publication
        RetryModulithPublicationCommand command = awaitRetryCommand(publicationId);
        assertThat(command.getIdentity().getIdempotenceId()).isEqualTo("retry:" + error.getId());

        await("error has been retried").atMost(FORTY_SECONDS)
                .until(() -> reload(error).getState() == ErrorState.TEMPORARY_RETRIED);
    }

    @Test
    void temporaryModulithFailure_escalatesToPermanentAfterMaxRetries() {
        // jeap.errorhandling.resend.default-resending-strategy.max-retries is 3 in the test configuration: the
        // publishing application reports the same publication as failed again after each retry, until the error
        // handling service stops retrying and escalates the publication to a manual task
        String publicationId = UUID.randomUUID().toString();
        for (int completionAttempt = 1; completionAttempt <= 4; completionAttempt++) {
            publishModulithFailure(publicationId, completionAttempt, Temporality.TEMPORARY);
            int expectedErrorCount = completionAttempt;
            await(expectedErrorCount + " errors have been recorded").atMost(FORTY_SECONDS)
                    .until(() -> errorRepository.findAll().size() == expectedErrorCount);
        }

        await("the last failure of the publication is escalated to a permanent error").atMost(FORTY_SECONDS)
                .until(() -> errorRepository.findAll().stream()
                        .anyMatch(error -> error.getState() == ErrorState.PERMANENT
                                || error.getState() == ErrorState.SEND_TO_MANUALTASK));

        // all failures belong to the same publication, so they share a single causing event
        assertThat(causingEventRepository.findAll()).hasSize(1);
    }

    // --- helpers -----------------------------------------------------------------------------------------

    private Error awaitSingleError() {
        await("an error has been recorded").atMost(FORTY_SECONDS)
                .until(() -> errorRepository.findAll().size() == 1);
        return errorRepository.findAll().getFirst();
    }

    private Error reload(Error error) {
        return errorRepository.findById(error.getId()).orElseThrow();
    }

    private String createAuthTokenForUserRoles(SemanticApplicationRole... userRoles) {
        return jwsBuilderFactory.createValidForFixedLongPeriodBuilder(SUBJECT, CONTEXT)
                .withUserRoles(userRoles)
                .build().serialize();
    }

    private void assertAuditLog(Error error, AuditLog.AuditedAction action) {
        List<AuditLog> auditLogs = auditLogRepository.findAllByErrorId(error.getId());
        assertThat(auditLogs).singleElement()
                .satisfies(auditLog -> {
                    assertThat(auditLog.getAction()).isEqualTo(action);
                    assertThat(auditLog.getUser().getSubject()).isEqualTo(SUBJECT);
                });
    }

    private void assertPublicationPersisted(Error error, String publicationId) {
        CausingEvent causingEvent = causingEventRepository.findByCausingEventId(publicationId).orElseThrow();
        assertThat(causingEvent.getOrigin()).isEqualTo(CausingEvent.Origin.MODULITH_PUBLICATION);
        ModulithPublicationData publication = causingEvent.getModulithPublication();
        assertThat(publication.getPublicationId()).isEqualTo(publicationId);
        assertThat(publication.getListener()).isEqualTo("example.OrderListener");
        assertThat(publication.getEventType()).isEqualTo("example.OrderPlacedEvent");
        assertThat(publication.getRetryCommandTopic()).isEqualTo(RETRY_COMMAND_TOPIC);
        assertThat(publication.getDiscardCommandTopic()).isEqualTo(DISCARD_COMMAND_TOPIC);
        assertThat(publication.getSerializedEvent()).isEqualTo(SERIALIZED_EVENT.getBytes(StandardCharsets.UTF_8));
        assertThat(error.getCausingEvent().getId()).isEqualTo(causingEvent.getId());
    }

    private RetryModulithPublicationCommand awaitRetryCommand(String publicationId) {
        await("retry command for publication " + publicationId).atMost(FORTY_SECONDS)
                .until(() -> commandConsumer.retryCommandFor(publicationId).isPresent());
        return commandConsumer.retryCommandFor(publicationId).orElseThrow();
    }

    private DiscardModulithPublicationCommand awaitDiscardCommand(String publicationId) {
        await("discard command for publication " + publicationId).atMost(FORTY_SECONDS)
                .until(() -> commandConsumer.discardCommandFor(publicationId).isPresent());
        return commandConsumer.discardCommandFor(publicationId).orElseThrow();
    }

    /**
     * Publishes a failure event as the Modulith error handling starter of the publishing application would, with the
     * idempotence id built from the publication id and the number of completion attempts, so that every report of the
     * same publication is a new error for the same causing event.
     */
    private ModulithPublicationProcessingFailedEvent publishModulithFailure(String publicationId, int completionAttempt,
                                                                           Temporality temporality) {
        ModulithPublicationProcessingFailedEvent event = ModulithPublicationProcessingFailedEvent.newBuilder()
                .setIdentity(AvroDomainEventIdentity.newBuilder()
                        .setEventId(UUID.randomUUID().toString())
                        .setIdempotenceId(publicationId + ":" + completionAttempt)
                        .setCreated(Instant.now())
                        .build())
                .setType(AvroDomainEventType.newBuilder()
                        .setName("ModulithPublicationProcessingFailedEvent")
                        .setVersion("1.0.0")
                        .build())
                .setPublisher(AvroDomainEventPublisher.newBuilder()
                        .setSystem("TEST")
                        .setService("order-service")
                        .build())
                .setReferences(ModulithPublicationProcessingFailedReferences.newBuilder()
                        .setPublication(ModulithPublicationReference.newBuilder()
                                .setType("modulithPublication")
                                .setPublicationId(publicationId)
                                .build())
                        .build())
                .setPayload(ModulithPublicationProcessingFailedPayload.newBuilder()
                        .setListener("example.OrderListener")
                        .setEventType("example.OrderPlacedEvent")
                        .setErrorMessage("Publication failed")
                        .setErrorDescription("Spring Modulith listener processing failed after %d completion attempts."
                                .formatted(completionAttempt))
                        .setStackTrace("stack trace")
                        .setStackTraceHash("stack-trace-hash")
                        .setTemporality(temporality)
                        .setSerializedEvent(ByteBuffer.wrap(SERIALIZED_EVENT.getBytes(StandardCharsets.UTF_8)))
                        .setSerializedEventContentType("application/json")
                        .setRetryCommandTopicName(RETRY_COMMAND_TOPIC)
                        .setDiscardCommandTopicName(DISCARD_COMMAND_TOPIC)
                        .build())
                .setDomainEventVersion("1.0.0")
                .build();
        kafkaTemplate.send(MODULITH_FAILURE_TOPIC, event);
        return event;
    }

    /**
     * Consumes the commands the error handling service sends back to the publishing application, standing in for the
     * Modulith error handling starter of that application.
     */
    @Component
    @Profile(PROFILE)
    static class ModulithCommandConsumer {

        // written by the Kafka listener threads and read by the test thread
        private final List<RetryModulithPublicationCommand> retryCommands = new CopyOnWriteArrayList<>();
        private final List<DiscardModulithPublicationCommand> discardCommands = new CopyOnWriteArrayList<>();

        @TestKafkaListener(topics = {RETRY_COMMAND_TOPIC}, groupId = "modulith-retry-command-consumer")
        public void consumeRetryCommand(RetryModulithPublicationCommand command) {
            log.info("Consuming retry command in ModulithCommandConsumer: {}", command);
            retryCommands.add(command);
        }

        @TestKafkaListener(topics = {DISCARD_COMMAND_TOPIC}, groupId = "modulith-discard-command-consumer")
        public void consumeDiscardCommand(DiscardModulithPublicationCommand command) {
            log.info("Consuming discard command in ModulithCommandConsumer: {}", command);
            discardCommands.add(command);
        }

        List<RetryModulithPublicationCommand> retryCommands() {
            return List.copyOf(retryCommands);
        }

        Optional<RetryModulithPublicationCommand> retryCommandFor(String publicationId) {
            return retryCommands.stream()
                    .filter(command -> publicationId.equals(command.getReferences().getPublication().getPublicationId()))
                    .findFirst();
        }

        Optional<DiscardModulithPublicationCommand> discardCommandFor(String publicationId) {
            return discardCommands.stream()
                    .filter(command -> publicationId.equals(command.getReferences().getPublication().getPublicationId()))
                    .findFirst();
        }

        void reset() {
            retryCommands.clear();
            discardCommands.clear();
        }
    }
}
