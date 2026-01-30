package ch.admin.bit.jeap.errorhandling;

import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.*;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error.ErrorState;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorEventData.Temporality;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import lombok.SneakyThrows;

import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ErrorStubs {

    public static final String CAUSING_EVENT_PUBLISHER_SERVICE = "causing-event-publisher-service";
    public static final String ERROR_EVENT_PUBLISHER_SERVICE = "error-event-publisher-service";
    public static final String TIMESTAMP = "2007-12-03 10:15:30";
    public static final String ERROR_CODE = "500";
    public static final String ERROR_MESSAGE = "error message";
    public static final String EVENT_NAME = "TestEvent";
    private static final String EVENT_PUBLISHER_SYSTEM = "TEST";
    private static final ZonedDateTime DATE_TIME = ZonedDateTime.parse("2007-12-03T10:15:30+01:00[Europe/Paris]");

    private ErrorStubs() {
    }

    public static Error createPermanentError() {
        return createError(Temporality.PERMANENT, ErrorState.PERMANENT, DATE_TIME, ERROR_EVENT_PUBLISHER_SERVICE, CAUSING_EVENT_PUBLISHER_SERVICE);
    }

    public static Error createTemporaryError(String errorEventService, String causingEventService) {
        return createError(Temporality.TEMPORARY, ErrorState.TEMPORARY_RETRY_PENDING, DATE_TIME, errorEventService, causingEventService);
    }

    public static Error createTemporaryError() {
        return createTemporaryError(ERROR_EVENT_PUBLISHER_SERVICE, CAUSING_EVENT_PUBLISHER_SERVICE);
    }

    public static Error createTemporaryErrorWithHeaders(MessageHeader... headers) {
        return createTemporaryErrorWithHeaders(ERROR_EVENT_PUBLISHER_SERVICE, CAUSING_EVENT_PUBLISHER_SERVICE, headers);
    }

    public static Error createTemporaryErrorWithHeaders(String errorEventService, String causingEventService, MessageHeader... headers) {
        return createError(Temporality.TEMPORARY, ErrorState.TEMPORARY_RETRY_PENDING, DATE_TIME, errorEventService, causingEventService, headers);
    }

    @SneakyThrows
    public static Error createTemporaryErrorWithAvroMagicBytePayload(String clusterName) {
        byte[] payload = new byte[100];
        SecureRandom.getInstanceStrong().nextBytes(payload);
        // Byte 0 is the confluent avro magic byte
        payload[0] = 0;
        return createError(Temporality.TEMPORARY, ErrorState.TEMPORARY_RETRY_PENDING, DATE_TIME, clusterName, payload, ERROR_EVENT_PUBLISHER_SERVICE, CAUSING_EVENT_PUBLISHER_SERVICE);
    }

    private static Error createError(Temporality temporality, ErrorState errorState, ZonedDateTime createdTimestamp, String errorEventService, String causingEventService, MessageHeader... headers) {
        return createError(temporality, errorState, createdTimestamp, KafkaProperties.DEFAULT_CLUSTER, "payload".getBytes(), errorEventService, causingEventService, headers);
    }

    private static Error createError(Temporality temporality, ErrorState errorState, ZonedDateTime createdTimestamp, String clusterName, byte[] payload, String errorEventService, String causingEventService, MessageHeader... headers) {
        List<MessageHeader> messageHeaders = new ArrayList<>();
        messageHeaders.add(MessageHeader.builder()
                .headerName("dummy")
                .headerValue("dummyValue".getBytes())
                .build());
        messageHeaders.add(MessageHeader.builder()
                .headerName("jeap-cert")
                .headerValue("jeap-cert-value".getBytes())
                .build());
        if (headers != null) {
            for (MessageHeader header : headers) {
                messageHeaders.add(header);
            }
        }

        EventPublisher errorEventPublisher = EventPublisher.builder()
                .system(EVENT_PUBLISHER_SYSTEM)
                .service(errorEventService)
                .build();

        EventPublisher causingEventPublisher = EventPublisher.builder()
                .system(EVENT_PUBLISHER_SYSTEM)
                .service(causingEventService)
                .build();
        EventMetadata causingEventMetadata = EventMetadata.builder()
                .created(DATE_TIME)
                .id(generateId())
                .idempotenceId(generateId())
                .publisher(causingEventPublisher)
                .type(EventType.builder()
                        .name(EVENT_NAME)
                        .version("1")
                        .build())
                .build();
        EventMetadata errorEventMetadata = EventMetadata.builder()
                .created(DATE_TIME)
                .id(generateId())
                .idempotenceId(generateId())
                .publisher(errorEventPublisher)
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
                        .metadata(causingEventMetadata)
                        .headers(messageHeaders)
                        .build())
                .errorEventData(ErrorEventData.builder()
                        .temporality(temporality)
                        .message(ERROR_MESSAGE)
                        .code(ERROR_CODE)
                        .description("error description")
                        .stackTrace("stack trace")
                        .stackTraceHash("stack trace hash")
                        .build())
                .errorEventMetadata(errorEventMetadata)
                .closingReason("")
                .created(createdTimestamp)
                .modified(createdTimestamp)
                .build();
    }

    private static String generateId() {
        return UUID.randomUUID().toString();
    }
}
