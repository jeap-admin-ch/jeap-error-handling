package ch.admin.bit.jeap.errorhandling;

import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventIdentity;
import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventPublisher;
import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventType;
import ch.admin.bit.jeap.errorhandling.event.test.TestEvent;
import ch.admin.bit.jeap.errorhandling.event.test.TestPayload;
import ch.admin.bit.jeap.errorhandling.event.test.TestReferences;
import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.DeadLetterTopicMetrics;
import ch.admin.bit.jeap.errorhandling.web.api.DeadLetterCountDTO;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageHandlerException;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageHandlerExceptionInformation;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedEvent;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedEventBuilder;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.messaging.kafka.serde.confluent.CustomKafkaAvroSerializer;
import ch.admin.bit.jeap.security.resource.semanticAuthentication.SemanticApplicationRole;
import ch.admin.bit.jeap.security.resource.token.JeapAuthenticationContext;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Exercises the dead-letter endpoints exactly as the UI calls them over HTTP: fetching the pending message
 * count ({@code GET /api/deadletter/count}) and starting a reactivation ({@code POST /api/deadletter/reactivate}),
 * including the role checks the UI relies on to show/hide actions.
 */
class DeadLetterReactivationControllerIT extends ErrorHandlingITBase {

    private static final String COUNT_URL = "/api/deadletter/count";
    private static final String REACTIVATE_URL = "/api/deadletter/reactivate";

    private static final SemanticApplicationRole VIEW_ROLE = SemanticApplicationRole.builder()
            .system("jme").resource("error").operation("view").build();
    private static final SemanticApplicationRole RETRY_ROLE = SemanticApplicationRole.builder()
            .system("jme").resource("error").operation("retry").build();
    private static final String SUBJECT = "69368608-D736-43C8-5F76-55B7BF168299";
    private static final JeapAuthenticationContext CONTEXT = JeapAuthenticationContext.SYS;

    private final RequestSpecification apiSpec;

    @Autowired
    private DeadLetterTopicMetrics deadLetterTopicMetrics;

    public DeadLetterReactivationControllerIT(@Value("${server.port}") int serverPort) {
        apiSpec = new RequestSpecBuilder().setPort(serverPort).build();
    }

    @Test
    void getDeadLetterCount_withoutViewRole_isForbidden() {
        given().
                spec(apiSpec).
                auth().oauth2(createAuthTokenForUserRoles(RETRY_ROLE)).
                when().
                get(COUNT_URL).
                then().
                statusCode(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void reactivateDeadLetters_withoutRetryRole_isForbidden() {
        given().
                spec(apiSpec).
                auth().oauth2(createAuthTokenForUserRoles(VIEW_ROLE)).
                when().
                post(REACTIVATE_URL + "?maxRecords=1").
                then().
                statusCode(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void getDeadLetterCount_and_reactivateDeadLetters_reflectThePendingMessagesOverHttp() {
        // baseline instead of an assumed zero: other tests sharing the embedded broker may have left pending
        // messages or committed progress on the dead letter topic
        deadLetterTopicMetrics.refresh();
        long baseline = getDeadLetterCountOverHttp();

        sendSync(DEAD_LETTER_TOPIC, createMessageProcessingFailedEvent());
        sendSync(DEAD_LETTER_TOPIC, createMessageProcessingFailedEvent());
        deadLetterTopicMetrics.refresh();

        assertThat(getDeadLetterCountOverHttp()).isEqualTo(baseline + 2);

        given().
                spec(apiSpec).
                auth().oauth2(createAuthTokenForUserRoles(RETRY_ROLE)).
                when().
                post(REACTIVATE_URL + "?maxRecords=2").
                then().
                statusCode(HttpStatus.ACCEPTED.value());

        // the count exposed over HTTP is refreshed once the reactivation triggered over HTTP has finished,
        // without waiting for the next scheduled refresh - this is what the UI relies on after a reactivation
        await("the HTTP-exposed count reflects the reactivation").atMost(FORTY_SECONDS)
                .until(() -> getDeadLetterCountOverHttp() == baseline);
    }

    private long getDeadLetterCountOverHttp() {
        return given().
                spec(apiSpec).
                auth().oauth2(createAuthTokenForUserRoles(VIEW_ROLE)).
                when().
                get(COUNT_URL).
                then().
                statusCode(HttpStatus.OK.value()).
                extract().as(DeadLetterCountDTO.class).
                getPendingMessageCount();
    }

    private String createAuthTokenForUserRoles(SemanticApplicationRole... userRoles) {
        return jwsBuilderFactory.createValidForFixedLongPeriodBuilder(SUBJECT, CONTEXT).
                withUserRoles(userRoles).
                build().serialize();
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
