package ch.admin.bit.jeap.errorhandling;

import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.*;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error.ErrorState;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorEventData.Temporality;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;

import java.time.ZonedDateTime;
import java.util.UUID;

public class ErrorStubs {

    public static final String EVENT_PUBLISHER_SERVICE = "test-service";
    public static final String TIMESTAMP = "2007-12-03 10:15:30";
    public static final String ERROR_CODE = "500";
    public static final String ERROR_MESSAGE = "error message";
    public static final String EVENT_NAME = "TestEvent";
    private static final String EVENT_PUBLISHER_SYSTEM = "TEST";
    private static final ZonedDateTime DATE_TIME = ZonedDateTime.parse("2007-12-03T10:15:30+01:00[Europe/Paris]");

    private ErrorStubs() {
    }

    public static Error createPermanentError() {
        return createError(Temporality.PERMANENT, ErrorState.PERMANENT, DATE_TIME);
    }

    public static Error createTemporaryError() {
        return createError(Temporality.TEMPORARY, ErrorState.TEMPORARY_RETRY_PENDING, DATE_TIME);
    }

    public static Error createTemporaryErrorWithRandomPayload(String clusterName) {
        return createError(Temporality.TEMPORARY, ErrorState.TEMPORARY_RETRY_PENDING, DATE_TIME, clusterName, UUID.randomUUID().toString().getBytes());
    }

    public static Error createTemporaryRetriedError() {
        return createError(Temporality.TEMPORARY, ErrorState.TEMPORARY_RETRIED, DATE_TIME);
    }

    public static Error createPermanentRetriedError() {
        return createError(Temporality.PERMANENT, ErrorState.PERMANENT_RETRIED, DATE_TIME);
    }

    private static Error createError(Temporality temporality, ErrorState errorState, ZonedDateTime createdTimestamp) {
        return createError(temporality, errorState, createdTimestamp, KafkaProperties.DEFAULT_CLUSTER, "payload".getBytes());
    }

    private static Error createError(Temporality temporality, ErrorState errorState, ZonedDateTime createdTimestamp, String clusterName, byte[] payload) {
        EventPublisher publisher = EventPublisher.builder()
                .system(EVENT_PUBLISHER_SYSTEM)
                .service(EVENT_PUBLISHER_SERVICE)
                .build();
        EventMetadata eventMetadata = EventMetadata.builder()
                .created(DATE_TIME)
                .id(generateId())
                .idempotenceId(generateId())
                .publisher(publisher)
                .type(EventType.builder()
                        .name(EVENT_NAME)
                        .version("1")
                        .build())
                .build();
        return Error.builder()
                .id(UUID.randomUUID())
                .state(errorState)
                .causingEvent(CausingEvent.builder()
                        .message(EventMessage.builder()
                                .key(null)
                                .payload(payload)
                                .topic("topic")
                                .partition(42)
                                .offset(303)
                                .clusterName(clusterName)
                                .build())
                        .metadata(eventMetadata)
                        .build())
                .errorEventData(ErrorEventData.builder()
                        .temporality(temporality)
                        .message(ERROR_MESSAGE)
                        .code(ERROR_CODE)
                        .description("error description")
                        .stackTrace("stack trace")
                        .stackTraceHash("stack trace hash")
                        .build())
                .errorEventMetadata(eventMetadata)
                .closingReason("")
                .created(createdTimestamp)
                .modified(createdTimestamp)
                .build();
    }

    private static String generateId() {
        return UUID.randomUUID().toString();
    }
}
