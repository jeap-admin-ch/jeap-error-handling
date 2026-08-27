package ch.admin.bit.jeap.errorhandling.domain.eventHandler;

import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.CausingEvent;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorEventData;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.EventMetadata;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.EventPublisher;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.EventType;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ModulithPublicationData;
import ch.admin.bit.jeap.messaging.kafka.tracing.TraceContext;
import ch.admin.bit.jeap.messaging.kafka.tracing.TraceContextProvider;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.OriginalTraceContext;
import ch.admin.bit.jeap.modulith.event.publicationprocessingfailed.ModulithPublicationProcessingFailedEvent;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.time.ZonedDateTime;

@Component
class ModulithErrorEventMapper {

    static final String ERROR_CODE = "MODULITH_PUBLICATION_PROCESSING_FAILED";

    private final TraceContextProvider traceContextProvider;

    ModulithErrorEventMapper(TraceContextProvider traceContextProvider) {
        this.traceContextProvider = traceContextProvider;
    }

    CausingEvent toCausingEvent(ModulithPublicationProcessingFailedEvent event) {
        var payload = event.getPayload();
        String publicationId = event.getReferences().getPublication().getPublicationId();
        var publisher = EventPublisher.builder()
                .system(event.getPublisher().getSystem())
                .service(event.getPublisher().getService())
                .build();
        var metadata = EventMetadata.builder()
                .id(publicationId)
                .idempotenceId(publicationId)
                .created(event.getIdentity().getCreatedZoned())
                .type(EventType.builder().name(payload.getEventType()).version("unknown").build())
                .publisher(publisher)
                .build();
        var publication = ModulithPublicationData.builder()
                .publicationId(publicationId)
                .listener(payload.getListener())
                .eventType(payload.getEventType())
                .serializedEvent(bytes(payload.getSerializedEvent()))
                .serializedEventContentType(payload.getSerializedEventContentType())
                .retryCommandTopic(payload.getRetryCommandTopicName())
                .discardCommandTopic(payload.getDiscardCommandTopicName())
                .build();
        return CausingEvent.builder()
                .origin(CausingEvent.Origin.MODULITH_PUBLICATION)
                .metadata(metadata)
                .modulithPublication(publication)
                .build();
    }

    Error toError(ModulithPublicationProcessingFailedEvent event, CausingEvent causingEvent) {
        var payload = event.getPayload();
        var errorMetadata = EventMetadata.builder()
                .id(event.getIdentity().getEventId())
                .idempotenceId(event.getIdentity().getIdempotenceId())
                .created(event.getIdentity().getCreatedZoned())
                .type(EventType.builder().name(event.getType().getName()).version(event.getType().getVersion()).build())
                .publisher(EventPublisher.builder()
                        .system(event.getPublisher().getSystem())
                        .service(event.getPublisher().getService())
                        .build())
                .build();
        return Error.builder()
                .state(Error.ErrorState.PERMANENT)
                .causingEvent(causingEvent)
                .errorEventData(ErrorEventData.builder()
                        .code(ERROR_CODE)
                        .temporality(ErrorEventData.Temporality.valueOf(payload.getTemporality().name()))
                        .message(payload.getErrorMessage())
                        .description(payload.getErrorDescription())
                        .stackTrace(payload.getStackTrace())
                        .stackTraceHash(payload.getStackTraceHash())
                        .build())
                .errorEventMetadata(errorMetadata)
                .created(ZonedDateTime.now())
                .closingReason("")
                .originalTraceContext(currentTraceContext())
                .build();
    }

    private OriginalTraceContext currentTraceContext() {
        TraceContext traceContext = traceContextProvider.getTraceContext();
        return traceContext == null ? null : OriginalTraceContext.builder()
                .traceIdHigh(traceContext.getTraceIdHigh())
                .traceId(traceContext.getTraceId())
                .spanId(traceContext.getSpanId())
                .parentSpanId(traceContext.getParentSpanId())
                .traceIdString(traceContext.getTraceIdString())
                .sampled(traceContext.getSampled())
                .build();
    }

    private static byte[] bytes(ByteBuffer value) {
        if (value == null) {
            return null;
        }
        ByteBuffer copy = value.asReadOnlyBuffer();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }
}
