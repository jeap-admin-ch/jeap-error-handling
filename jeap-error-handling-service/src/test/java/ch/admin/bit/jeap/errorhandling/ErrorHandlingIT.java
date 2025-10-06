package ch.admin.bit.jeap.errorhandling;

import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventIdentity;
import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventPublisher;
import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventType;
import ch.admin.bit.jeap.errorhandling.command.test.TestCommand;
import ch.admin.bit.jeap.errorhandling.command.test.TestCommandPayload;
import ch.admin.bit.jeap.errorhandling.command.test.TestCommandReferences;
import ch.admin.bit.jeap.errorhandling.event.test.TestEvent;
import ch.admin.bit.jeap.errorhandling.event.test.TestPayload;
import ch.admin.bit.jeap.errorhandling.event.test.TestReferences;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.AuditLog;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error.ErrorState;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorEventData.Temporality;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorGroup;
import ch.admin.bit.jeap.errorhandling.web.api.ErrorDTO;
import ch.admin.bit.jeap.messaging.avro.*;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageHandlerExceptionInformation;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedEvent;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedEventBuilder;
import ch.admin.bit.jeap.messaging.kafka.crypto.JeapKafkaAvroSerdeCryptoConfig;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.messaging.kafka.serde.confluent.CustomKafkaAvroSerializer;
import ch.admin.bit.jeap.security.resource.semanticAuthentication.SemanticApplicationRole;
import ch.admin.bit.jeap.security.resource.token.JeapAuthenticationContext;
import ch.admin.bit.jeap.security.test.jws.JwsBuilder;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ch.admin.bit.jeap.errorhandling.infrastructure.persistence.AuditLog.AuditedAction.DELETE_ERROR;
import static ch.admin.bit.jeap.errorhandling.infrastructure.persistence.AuditLog.AuditedAction.RESEND_CAUSING_EVENT;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

@SpringBootTest(webEnvironment = DEFINED_PORT,
        properties = {"server.port=8304",
                "jeap.errorhandling.deadLetterTopicName=" + ErrorHandlingITBase.ERROR_TOPIC,
                "jeap.errorhandling.topic=${jeap.messaging.kafka.errorTopicName}",
                "jeap.security.oauth2.resourceserver.authorization-server.issuer=" + JwsBuilder.DEFAULT_ISSUER,
                "jeap.security.oauth2.resourceserver.authorization-server.jwk-set-uri=http://localhost:${server.port}/.well-known/jwks.json",
                "logging.level.ch.admin.bit.jeap.errorhandling=DEBUG",
                "jeap.errorhandling.metrics.updateFrequencyMillis=500"
        })
@DirtiesContext
class ErrorHandlingIT extends ErrorHandlingITBase {

    private static final String SUBJECT = "69368608-D736-43C8-5F76-55B7BF168299";
    private static final JeapAuthenticationContext CONTEXT = JeapAuthenticationContext.SYS;
    private final RequestSpecification apiSpec;

    private static final SemanticApplicationRole VIEW_ROLE = SemanticApplicationRole.builder()
            .system("jme")
            .resource("error")
            .operation("view")
            .build();

    private static final SemanticApplicationRole RETRY_ROLE = SemanticApplicationRole.builder()
            .system("jme")
            .resource("error")
            .operation("retry")
            .build();

    private static final SemanticApplicationRole DELETE_ROLE = SemanticApplicationRole.builder()
            .system("jme")
            .resource("error")
            .operation("delete")
            .build();

    private static void assertEventId(String domainEventId, TestEvent consumedEvent) {
        assertEquals(domainEventId, consumedEvent.getIdentity().getEventId());
    }

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

    private static TestCommand createTestCommand(String messagePayload) {
        return TestCommand.newBuilder()
                .setType(AvroMessageType.newBuilder()
                        .setName("TestCommand")
                        .setVersion("1")
                        .build())
                .setReferences(TestCommandReferences.newBuilder().build())
                .setIdentity(AvroMessageIdentity.newBuilder()
                        .setId(UUID.randomUUID().toString())
                        .setIdempotenceId(UUID.randomUUID().toString())
                        .setCreated(Instant.now())
                        .build())
                .setPayload(TestCommandPayload.newBuilder()
                        .setMessage(messagePayload)
                        .build())
                .setPublisher(AvroMessagePublisher.newBuilder()
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

    public ErrorHandlingIT(@Value("${server.port}") int serverPort) {
        apiSpec = new RequestSpecBuilder()
                .setPort(serverPort).build();
    }

    @Test
    void testCanConsumeMessageProcessingFailedEvent() {
        // given
        TestEvent testEvent = createTestEvent("unexpected error");
        MessageProcessingFailedEvent errorEvent = createMessageProcessingFailedEvent(testEvent);
        assertNotNull(errorEvent.getPayload().getFailedMessageMetadata());

        // when
        kafkaTemplate.send(ERROR_TOPIC, errorEvent);

        // then
        Error error = awaitSingleErrorInRepository();
        assertSame(Temporality.PERMANENT, error.getErrorEventData().getTemporality());
        assertEquals(errorEvent.getIdentity().getEventId(), error.getErrorEventMetadata().getId());
        assertEquals(testEvent.getIdentity().getId(), error.getCausingEventMetadata().getId());
    }

    @Test
    @Transactional // for lazily fetched objects
    void testCanGroupErrorsOfConsumedMessageProcessingFailedEvents() {
        // given
        TestEvent testEvent1 = createTestEvent("unexpected error");
        MessageProcessingFailedEvent errorEvent1 = createMessageProcessingFailedEvent(testEvent1);
        TestEvent testEvent2 = createTestEvent("unexpected error again");
        MessageProcessingFailedEvent errorEvent2 = createMessageProcessingFailedEvent(testEvent2);


        // when
        kafkaTemplate.send(ERROR_TOPIC, errorEvent1);
        kafkaTemplate.send(ERROR_TOPIC, errorEvent2);

        // then
        List<Error> errors = awaitErrorsInRepository(2);
        List<ErrorGroup> errorGroups = errorGroupRepository.findAll();
        assertThat(errorGroups).hasSize(1);
        ErrorGroup errorGroup = errorGroups.getFirst();
        assertThat(errors.get(0).getErrorGroup()).isEqualTo(errorGroup);
        assertThat(errors.get(1).getErrorGroup()).isEqualTo(errorGroup);
        assertThat(errorGroup.getErrorPublisher()).isEqualTo(errorEvent1.getPublisher().getService());
        assertThat(errorGroup.getEventName()).isEqualTo(testEvent1.getType().getName());
        assertThat(errorGroup.getErrorStackTraceHash()).isEqualTo(errorEvent1.getPayload().getStackTraceHash());
        assertThat(errorGroup.getErrorCode()).isEqualTo(errorEvent1.getReferences().getErrorType().getCode());
        assertThat(errorGroup.getErrorMessage()).isEqualTo(errorEvent1.getPayload().getErrorMessage());
    }

    @Test
    void testCanConsumeMessageProcessingFailedEvent_forCommand() {
        // given
        TestCommand testCommand = createTestCommand("unexpected error");
        MessageProcessingFailedEvent errorEvent = createMessageProcessingFailedEvent(testCommand);

        // when
        kafkaTemplate.send(ERROR_TOPIC, errorEvent);

        // then
        Error error = awaitSingleErrorInRepository();
        assertSame(Temporality.PERMANENT, error.getErrorEventData().getTemporality());
        assertEquals(errorEvent.getIdentity().getEventId(), error.getErrorEventMetadata().getId());
        assertEquals(testCommand.getIdentity().getId(), error.getCausingEventMetadata().getId());
    }


    @Test
    void testCanConsumeMessageProcessingFailedEvent_withMetadataFromDeserializedEvent() {
        // given
        TestEvent testEvent = createTestEvent("unexpected error");
        MessageProcessingFailedEvent errorEvent = createMessageProcessingFailedEvent(testEvent);
        errorEvent.getPayload().setFailedMessageMetadata(null);

        // when
        kafkaTemplate.send(ERROR_TOPIC, errorEvent);

        // then
        Error error = awaitSingleErrorInRepository();
        assertSame(Temporality.PERMANENT, error.getErrorEventData().getTemporality());
        assertEquals(errorEvent.getIdentity().getEventId(), error.getErrorEventMetadata().getId());
        assertEquals(testEvent.getIdentity().getId(), error.getCausingEventMetadata().getId());
    }

    @Test
    void testUnknownTemporalityFailure_expectStoredInRepositoryWithoutRetry() {
        // given
        TestEvent domainEvent = createTestEvent("unexpected error");

        // when
        kafkaTemplate.send(DOMAIN_EVENT_TOPIC, domainEvent);

        // then
        Error error = awaitSingleErrorInRepository();
        assertSame(Temporality.UNKNOWN, error.getErrorEventData().getTemporality());
        assertEquals(1, errorRepository.countErrorsForCausingEvent(error.getCausingEventMetadata().getId()));
    }

    @Test
    void testTemporaryFailure_expectRepublishedToOriginalTopic() {
        // given
        TestEvent domainEvent = createTestEvent(TEMPORARY_ERROR);
        String domainEventId = domainEvent.getIdentity().getEventId();

        // when
        kafkaTemplate.send(DOMAIN_EVENT_TOPIC, domainEvent);

        // Then wait until failure has been received and stored in repository
        Error error = awaitSingleErrorInRepository();
        assertSame(Temporality.TEMPORARY, error.getErrorEventData().getTemporality());

        // Then wait until the event has been republished to the consumer
        await("event is republished to consumer").atMost(FORTY_SECONDS)
                .until(() -> !testConsumer.getConsumedEventsByIdempotenceId(domainEvent.getIdentity().getIdempotenceId()).isEmpty());

        // Then assert that the event with the same event ID has been republished
        List<TestEvent> consumedEvents = testConsumer.getConsumedEventsByIdempotenceId(domainEvent.getIdentity().getIdempotenceId());
        assertEventId(domainEventId, consumedEvents.getFirst());

        // Then assert that event has been rescheduled
        await("all events marked as resent").atMost(FORTY_SECONDS)
                .until(() -> !scheduledResendRepository.findByErrorId(error.getId()).isEmpty() && scheduledResendRepository.findByErrorId(error.getId()).stream()
                        .allMatch(sr -> sr.getResentAt() != null));

        // Then assert that more errors have been created
        await("errors have been created").atMost(FORTY_SECONDS)
                .until(() -> errorRepository.countErrorsForCausingEvent(domainEventId) > 1);
    }

    @Test
    void testPermanentFailure_expectStoredInRepositoryWithoutRetry() {
        // given
        TestEvent domainEvent = createTestEvent(PERMANENT_ERROR);

        // when
        kafkaTemplate.send(DOMAIN_EVENT_TOPIC, domainEvent);

        // then
        Error error = awaitSingleErrorInRepository();
        assertSame(Temporality.PERMANENT, error.getErrorEventData().getTemporality());
        assertEquals(1, errorRepository.countErrorsForCausingEvent(domainEvent.getIdentity().getEventId()));
    }

    @Test
    void testDeleteError_expectLoggedInAuditLog() {
        // given a permanent error
        TestEvent domainEvent = createTestEvent(PERMANENT_ERROR);
        kafkaTemplate.send(DOMAIN_EVENT_TOPIC, domainEvent);
        Error error = awaitSingleErrorInRepository();
        assertEquals(1, errorRepository.countErrorsForCausingEvent(domainEvent.getIdentity().getEventId()));

        // when error is deleted
        given().
                spec(apiSpec).
                auth().oauth2(createAuthTokenForUserRoles(DELETE_ROLE)).
                when().
                delete("/api/error/{errorId}", error.getId()).
                then().
                statusCode(HttpStatus.OK.value());

        // then error deletion has been registered in the audit log
        List<AuditLog> auditLogsErrorDeleted = auditLogRepository.findAllByErrorId(error.getId());
        assertEquals(1, auditLogsErrorDeleted.size());
        AuditLog auditLogErrorDeleted = auditLogsErrorDeleted.getFirst();
        assertEquals(DELETE_ERROR, auditLogErrorDeleted.getAction());
        assertEquals(SUBJECT, auditLogErrorDeleted.getUser().getSubject());
        assertEquals(CONTEXT.name(), auditLogErrorDeleted.getUser().getAuthContext());
    }

    //@formatter:off
    @Test
    void testError_expectDetailsRetrievableViaApi() {
        // given
        TestEvent domainEvent = createTestEvent(PERMANENT_ERROR);

        // when
        kafkaTemplate.send(DOMAIN_EVENT_TOPIC, domainEvent);

        // then
        Error error = awaitSingleErrorInRepository();

        // Check if error details can be read
        String authToken = createAuthTokenForUserRoles(VIEW_ROLE);
        ErrorDTO errorDTO =
                given().
                        spec(apiSpec).
                        auth().oauth2(authToken).
                        when().
                        get("/api/error/{errorId}/details", error.getId()).
                        then().
                        statusCode(HttpStatus.OK.value()).
                        extract().as(ErrorDTO.class);
        assertEquals(error.getId().toString(), errorDTO.getId());
        assertEquals(error.getErrorEventData().getStackTrace(), errorDTO.getStacktrace());

        // Check if the event payload can be read as JSON
        String eventAsJsonString =
                given().
                        spec(apiSpec).
                        auth().oauth2(createAuthTokenForUserRoles(VIEW_ROLE)).
                        when().
                        get("/api/error/{errorId}/event/payload", error.getId()).
                        then().
                        statusCode(HttpStatus.OK.value()).
                        extract().asString();
        assertTrue(eventAsJsonString.contains("\"name\" : \"TestEvent\""));
    }

    @Test
    void testError_expectDetailsRetrievableViaApi_forCommand() {
        // given
        TestCommand testCommand = createTestCommand(PERMANENT_ERROR);

        // when
        kafkaTemplate.send(COMMAND_TOPIC, testCommand);

        // then
        Error error = awaitSingleErrorInRepository();

        // Check if error details can be read
        String authToken = createAuthTokenForUserRoles(VIEW_ROLE);
        ErrorDTO errorDTO =
                given().
                        spec(apiSpec).
                        auth().oauth2(authToken).
                        when().
                        get("/api/error/{errorId}/details", error.getId()).
                        then().
                        statusCode(HttpStatus.OK.value()).
                        extract().as(ErrorDTO.class);
        assertEquals(error.getId().toString(), errorDTO.getId());
        assertEquals(error.getErrorEventData().getStackTrace(), errorDTO.getStacktrace());

        // Check if the event payload can be read as JSON
        String commandAsJsonString =
                given().
                        spec(apiSpec).
                        auth().oauth2(createAuthTokenForUserRoles(VIEW_ROLE)).
                        when().
                        get("/api/error/{errorId}/event/payload", error.getId()).
                        then().
                        statusCode(HttpStatus.OK.value()).
                        extract().asString();
        assertTrue(commandAsJsonString.contains("\"name\" : \"TestCommand\""));
    }

    @Test
    void testRetry_verifySameEventIsSentToConsumerAgain() {
        // given
        TestEvent domainEvent = createTestEvent(RETRY_SUCCESS);

        // when
        kafkaTemplate.send(DOMAIN_EVENT_TOPIC, domainEvent);

        // then
        Error error = awaitSingleErrorInRepository();
        assertEquals(ErrorState.PERMANENT, error.getState());
        assertTrue(auditLogRepository.findAllByErrorId(error.getId()).isEmpty());

        given().
                spec(apiSpec).
                auth().oauth2(createAuthTokenForUserRoles(RETRY_ROLE)).
                when().
                post("/api/error/{errorId}/event/retry", error.getId()).
                then().
                statusCode(HttpStatus.OK.value());

        // Then wait until the event has been republished to the consumer and been accepted there
        await("event is republished to consumer and accepted as successful retry").atMost(FORTY_SECONDS)
                .until(() -> testConsumer.isSuccessfulRetrySimulated());

        List<TestEvent> consumedEvents = testConsumer.getConsumedEventsByIdempotenceId(domainEvent.getIdentity().getIdempotenceId());
        TestEvent originalEventCausingError = consumedEvents.get(0);
        TestEvent reSentEvent = consumedEvents.get(1);
        assertEquals(originalEventCausingError, reSentEvent, "Re-sent event equals original event");

        // Make sure no new error has been created
        Error errorRetried = awaitSingleErrorInRepository();

        // Check that the resend of the causing event has been registered in the audit log
        List<AuditLog> auditLogsErrorRetried = auditLogRepository.findAllByErrorId(errorRetried.getId());
        assertEquals(1, auditLogsErrorRetried.size());
        AuditLog auditLogErrorRetry = auditLogsErrorRetried.getFirst();
        assertEquals(RESEND_CAUSING_EVENT, auditLogErrorRetry.getAction());
        assertEquals(SUBJECT, auditLogErrorRetry.getUser().getSubject());
        assertEquals(CONTEXT.name(), auditLogErrorRetry.getUser().getAuthContext());
    }

    @Test
    void testRetry_verifyOriginalHeaderIsSentToConsumerAgain() {
        // given
        TestEvent domainEvent = createTestEvent(RETRY_SUCCESS);

        // when
        ProducerRecord<AvroMessageKey,AvroMessage> producerRecord =
                new ProducerRecord<>(DOMAIN_EVENT_TOPIC, domainEvent);
        String headerName = JeapKafkaAvroSerdeCryptoConfig.ENCRYPTED_VALUE_HEADER_NAME;
        byte[] headerValue = JeapKafkaAvroSerdeCryptoConfig.ENCRYPTED_VALUE_HEADER_TRUE;
        String headerNameCert = "jeap-cert";
        byte[] headerValueCert = {1, 2, 3 }; // dummy value
        String headerNameSign = "jeap-sign";
        byte[] headerValueSign = {1, 2, 3, 4 }; // dummy value
        String headerNameSignKey = "jeap-sign-key";
        byte[] headerValueSignKey = {1, 2, 3, 4, 5 }; // dummy value

        producerRecord.headers().add(headerName, headerValue);
        producerRecord.headers().add(headerNameCert, headerValueCert);
        producerRecord.headers().add(headerNameSign, headerValueSign);
        producerRecord.headers().add(headerNameSignKey, headerValueSignKey);
        kafkaTemplate.send(producerRecord);

        // then
        Error error = awaitSingleErrorInRepository();
        assertEquals(ErrorState.PERMANENT, error.getState());

        given().
                spec(apiSpec).
                auth().oauth2(createAuthTokenForUserRoles(RETRY_ROLE)).
                when().
                post("/api/error/{errorId}/event/retry", error.getId()).
                then().
                statusCode(HttpStatus.OK.value());

        // Then wait until the event has been republished to the consumer and been accepted there
        await("event is republished to consumer and accepted as successful retry").atMost(FORTY_SECONDS)
                .until(() -> testConsumer.isSuccessfulRetrySimulated());

        List<TestEvent> consumedEvents = testConsumer.getConsumedEventsByIdempotenceId(domainEvent.getIdentity().getIdempotenceId());
        TestEvent originalEventCausingError = consumedEvents.get(0);
        TestEvent reSentEvent = consumedEvents.get(1);
        assertEquals(originalEventCausingError, reSentEvent, "Re-sent event equals original event");
        List<ConsumerRecord<AvroMessageKey,TestEvent>> consumedRecords = testConsumer.getConsumedRecordsByIdempotenceId(domainEvent.getIdentity().getIdempotenceId());
        for(ConsumerRecord<AvroMessageKey,TestEvent> record : consumedRecords) {
            assertNotNull(getHeaderValue(JeapKafkaAvroSerdeCryptoConfig.ENCRYPTED_VALUE_HEADER_NAME, record),"Header value from original message is passed through");
            assertNotNull(getHeaderValue(headerNameCert, record),"Header value from original message is passed through");
            assertNotNull(getHeaderValue(headerNameSign, record),"Header value from original message is passed through");
            assertNotNull(getHeaderValue(headerNameSignKey, record),"Header value from original message is passed through");
        }
    }

    private Object getHeaderValue(String headerName, ConsumerRecord<AvroMessageKey,TestEvent> record) {
        if(record.headers() == null)        {
            return null;}

        return record.headers().lastHeader(headerName);
    }

    @Test
    void testRetry_verifySameCommandIsSentToConsumerAgain() {
        // given
        TestCommand testCommand = createTestCommand(RETRY_SUCCESS);

        // when
        kafkaTemplate.send(COMMAND_TOPIC, testCommand);

        // then
        Error error = awaitSingleErrorInRepository();
        assertEquals(ErrorState.PERMANENT, error.getState());

        given().
                spec(apiSpec).
                auth().oauth2(createAuthTokenForUserRoles(RETRY_ROLE)).
                when().
                post("/api/error/{errorId}/event/retry", error.getId()).
                then().
                statusCode(HttpStatus.OK.value());

        // Then wait until the command has been republished to the consumer and been accepted there
        await("command is republished to consumer and accepted as successful retry").atMost(FORTY_SECONDS)
                .until(() -> testConsumer.isSuccessfulRetrySimulated());

        List<TestCommand> consumedCommands = testConsumer.getConsumedCommandsByIdempotenceId(testCommand.getIdentity().getIdempotenceId());
        TestCommand originalCommandCausingError = consumedCommands.get(0);
        TestCommand reSentCommand = consumedCommands.get(1);
        assertEquals(originalCommandCausingError, reSentCommand, "Re-sent command equals original event");

        // Make sure no new error has been created
        awaitSingleErrorInRepository();
    }

    @Test
    void testExposesMetrics() {
        // Create two permanent errors with the same stack trace hash (i.e. belonging to the same error group)
        // (Makes the test not depend on other tests for creating errors.)
        TestEvent testEvent1 = createTestEvent("some error");
        MessageProcessingFailedEvent errorEvent1 = createMessageProcessingFailedEvent(testEvent1);
        TestEvent testEvent2 = createTestEvent("another error");
        MessageProcessingFailedEvent errorEvent2 = createMessageProcessingFailedEvent(testEvent2);
        kafkaTemplate.send(ERROR_TOPIC, errorEvent1);
        kafkaTemplate.send(ERROR_TOPIC, errorEvent2);
        awaitErrorsInRepository(2);
        assertThat(errorGroupRepository.findAll()).hasSize(1);

        awaitNonZeroMetricValue("eh_permanent_open");
        awaitNonZeroMetricValue("eh_error_groups_with_open_errors");
    }

    private void awaitNonZeroMetricValue(String metricName) {
        await("metric " + metricName + " is non-zero")
                .atMost(Duration.ofSeconds(30))
                .until(() -> {
                    String metrics = fetchMetrics();
                    String value = extractMetricValue(metricName, metrics);
                    return value != null && !value.equals("0.0");
                });
    }

    private String extractMetricValue(String metricName, String metrics) {
        Pattern pattern = Pattern.compile("^" + metricName + " (\\d+\\.\\d+)$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(metrics);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return null;
        }
    }

    private String fetchMetrics() {
        return given().
                   spec(apiSpec).
                    auth().preemptive().basic("prometheus", "test").
                when().
                    get("/actuator/prometheus").
                then().
                    statusCode(HttpStatus.OK.value()).
                    extract().asString();
    }

    //@formatter:on

    private Error awaitSingleErrorInRepository() {
        await("failure has been recorded in repository").atMost(FORTY_SECONDS)
                .until(() -> !errorRepository.findAll().isEmpty());

        return assertAndGetSingleFailure();
    }

    @SuppressWarnings("SameParameterValue")
    private List<Error> awaitErrorsInRepository(int numErrors) {
        await(numErrors + " failures have been recorded in repository").atMost(FORTY_SECONDS)
                .until(() -> errorRepository.findAll().size() == numErrors);
        return errorRepository.findAll();
    }

    private Error assertAndGetSingleFailure() {
        List<Error> all = errorRepository.findAll();

        assertEquals(1, all.size(), () -> "Expected single failure in repository: " + all);
        return all.getFirst();
    }

    private String createAuthTokenForUserRoles(SemanticApplicationRole... userroles) {
        return jwsBuilderFactory.createValidForFixedLongPeriodBuilder(SUBJECT, CONTEXT).
                withUserRoles(userroles).
                build().serialize();
    }
}
