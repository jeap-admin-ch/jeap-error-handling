package ch.admin.bit.jeap.errorhandling.domain.eventHandler;

import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.CausingEvent;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorEventData;
import ch.admin.bit.jeap.messaging.kafka.tracing.TraceContextProvider;
import ch.admin.bit.jeap.modulith.event.publicationprocessingfailed.ModulithPublicationProcessingFailedEvent;
import ch.admin.bit.jeap.modulith.event.publicationprocessingfailed.ModulithPublicationProcessingFailedPayload;
import ch.admin.bit.jeap.modulith.event.publicationprocessingfailed.Temporality;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModulithErrorEventMapperTest {

    private final TraceContextProvider traceContextProvider = mock(TraceContextProvider.class);
    private final ModulithErrorEventMapper mapper = new ModulithErrorEventMapper(traceContextProvider);

    @Test
    void mapsModulithFailureToPermanentError() {
        ZonedDateTime created = ZonedDateTime.now();
        byte[] serializedEvent = {1, 2, 3};
        ModulithPublicationProcessingFailedEvent event = failureEvent(Temporality.PERMANENT, created, serializedEvent);

        CausingEvent causingEvent = mapper.toCausingEvent("aws", event);
        Error error = mapper.toError(event, causingEvent);

        assertEquals(CausingEvent.Origin.MODULITH_PUBLICATION, causingEvent.getOrigin());
        assertEquals("aws", causingEvent.getModulithPublication().getClusterName());
        assertEquals("publication-id", causingEvent.getMetadata().getId());
        assertEquals("publication-id", causingEvent.getMetadata().getIdempotenceId());
        assertEquals("example.Event", causingEvent.getMetadata().getType().getName());
        assertEquals("listener-id", causingEvent.getModulithPublication().getListener());
        assertArrayEquals(serializedEvent, causingEvent.getModulithPublication().getSerializedEvent());
        assertEquals("retry-topic", causingEvent.getModulithPublication().getRetryCommandTopic());
        assertEquals("discard-topic", causingEvent.getModulithPublication().getDiscardCommandTopic());
        assertEquals(Error.ErrorState.PERMANENT, error.getState());
        assertEquals(ErrorEventData.Temporality.PERMANENT, error.getErrorEventData().getTemporality());
        assertEquals(ModulithErrorEventMapper.ERROR_CODE, error.getErrorEventData().getCode());
        assertEquals("processing failed", error.getErrorEventData().getMessage());
        assertEquals("error-event-id", error.getErrorEventMetadata().getId());
        assertNull(error.getOriginalTraceContext());
    }

    @Test
    void mapsTemporaryModulithFailureToTemporaryError() {
        ModulithPublicationProcessingFailedEvent event =
                failureEvent(Temporality.TEMPORARY, ZonedDateTime.now(), new byte[]{1});

        Error error = mapper.toError(event, mapper.toCausingEvent("aws", event));

        assertEquals(ErrorEventData.Temporality.TEMPORARY, error.getErrorEventData().getTemporality());
        assertEquals(Error.ErrorState.TEMPORARY_RETRY_PENDING, error.getState());
    }

    private static ModulithPublicationProcessingFailedEvent failureEvent(Temporality temporality,
                                                                        ZonedDateTime created,
                                                                        byte[] serializedEvent) {
        ModulithPublicationProcessingFailedEvent event = mock(ModulithPublicationProcessingFailedEvent.class,
                RETURNS_DEEP_STUBS);
        ModulithPublicationProcessingFailedPayload payload = mock(ModulithPublicationProcessingFailedPayload.class);
        when(event.getPayload()).thenReturn(payload);
        when(event.getIdentity().getEventId()).thenReturn("error-event-id");
        when(event.getIdentity().getIdempotenceId()).thenReturn("error-idempotence-id");
        when(event.getIdentity().getCreatedZoned()).thenReturn(created);
        when(event.getType().getName()).thenReturn("ModulithPublicationProcessingFailedEvent");
        when(event.getType().getVersion()).thenReturn("1.0.0");
        when(event.getPublisher().getSystem()).thenReturn("test-system");
        when(event.getPublisher().getService()).thenReturn("test-service");
        when(event.getReferences().getPublication().getPublicationId()).thenReturn("publication-id");
        when(payload.getListener()).thenReturn("listener-id");
        when(payload.getEventType()).thenReturn("example.Event");
        when(payload.getSerializedEvent()).thenReturn(ByteBuffer.wrap(serializedEvent));
        when(payload.getSerializedEventContentType()).thenReturn("application/json");
        when(payload.getRetryCommandTopicName()).thenReturn("retry-topic");
        when(payload.getDiscardCommandTopicName()).thenReturn("discard-topic");
        when(payload.getTemporality()).thenReturn(temporality);
        when(payload.getErrorMessage()).thenReturn("processing failed");
        when(payload.getErrorDescription()).thenReturn("description");
        when(payload.getStackTrace()).thenReturn("stack trace");
        when(payload.getStackTraceHash()).thenReturn("stack-trace-hash");
        return event;
    }
}
