package ch.admin.bit.jeap.errorhandling.infrastructure.persistence;

import ch.admin.bit.jeap.errorhandling.domain.housekeeping.RepositoryHousekeeping;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error.ErrorState;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@Import(PersistenceTestConfig.class)
class ErrorRepositoryTest {

    @Autowired
    private ErrorRepository errorRepository;
    @Autowired
    private CausingEventRepository causingEventRepository;

    @BeforeEach
    void saveTestData() {
        EventMetadata metadata = getEventMetadata("event-id-1");
        CausingEvent causingEvent = saveCausingEvent(metadata);
        // Save Errors with different states, ErrorState.ordinal + 1 times to get different counts per state
        Stream.of(ErrorState.values())
                .forEach(value -> saveMultipleErrorsWithState(value, causingEvent));
    }

    @Test
    void countErrorsInStateTemporaryRetryPending() {
        int expectedCount = ErrorState.TEMPORARY_RETRY_PENDING.ordinal() + 1;
        assertEquals(expectedCount, errorRepository.countErrorsInStateTemporaryRetryPending());
    }

    @Test
    void countErrorsInStatesPermanentOrSendToManualTask() {
        int expectedCount = ErrorState.PERMANENT.ordinal() + 1 +
                ErrorState.SEND_TO_MANUALTASK.ordinal() + 1;
        assertEquals(expectedCount, errorRepository.countErrorsInStatesPermanentOrSendToManualTask());
    }

    @Test
    void countErrorsInStateSendToManualTask() {
        int expectedCount = ErrorState.SEND_TO_MANUALTASK.ordinal() + 1;
        assertEquals(expectedCount, errorRepository.countErrorsInStateSendToManualTask());
    }

    @Test
    void countErrorsInStateDeleteOnManualTask() {
        int expectedCount = ErrorState.DELETE_ON_MANUALTASK.ordinal() + 1;
        assertEquals(expectedCount, errorRepository.countErrorsInStateDeleteOnManualTask());
    }

    @Test
    void countErrorsInStateResolveOnManualTask() {
        int expectedCount = ErrorState.RESOLVE_ON_MANUALTASK.ordinal() + 1;
        assertEquals(expectedCount, errorRepository.countErrorsInStateResolveOnManualTask());
    }

    @Test
    void countErrorsByErrorEventIdempotenceId() {
        Error error = errorRepository.findAll().get(0);
        int counterNonExisting = errorRepository.countErrorsByErrorEventIdempotenceId("does-not-exist");
        int counterOneError = errorRepository.countErrorsByErrorEventIdempotenceId(error.getErrorEventMetadata().getIdempotenceId());

        assertEquals(0, counterNonExisting);
        assertEquals(1, counterOneError);
    }

    @Test
    void findIdByStateInAndCreatedBefore() {
        int expectedErrorsRemoved = 4;
        saveErrorWithOldData();
        final Slice<ErrorQueryResult> errorList = errorRepository.findIdByStateInAndCreatedBefore(RepositoryHousekeeping.ERROR_STATES, ZonedDateTime.now().minusYears(1), Pageable.ofSize(10));
        assertThat(errorList).hasSize(expectedErrorsRemoved);
    }

    @Test
    void getAllEventSources() {
        List<String> eventSources = errorRepository.getAllEventSources();
        assertEquals(1, eventSources.size());
        assertEquals("service", eventSources.get(0));
    }

    @Test
    void getAllErrorCodes() {
        List<String> errorCodes = errorRepository.getAllErrorCodes();
        assertEquals(1, errorCodes.size());
        assertEquals("errorCode1", errorCodes.get(0));
    }

    private void saveMultipleErrorsWithState(ErrorState state, CausingEvent causingEvent) {
        int errorCounter = state.ordinal() + 1;
        for (int i = 0; i < errorCounter; i++) {
            saveError(state, causingEvent);
        }
    }

    private void saveError(ErrorState errorState, CausingEvent causingEvent) {
        storeError(causingEvent, errorState, ZonedDateTime.now());
    }

    @NotNull
    private CausingEvent saveCausingEvent(EventMetadata metadata) {
        CausingEvent causingEvent = CausingEvent.builder()
                .message(EventMessage.builder()
                        .offset(1)
                        .payload("test".getBytes(StandardCharsets.UTF_8))
                        .topic("topic")
                        .clusterName("clusterName")
                        .build())
                .metadata(metadata)
                .build();
        causingEventRepository.save(causingEvent);
        return causingEvent;
    }

    private EventMetadata getEventMetadata(String eventId) {
        return EventMetadata.builder()
                .id(eventId)
                .created(ZonedDateTime.now())
                .idempotenceId("idem-" + eventId)
                .publisher(EventPublisher.builder()
                        .service("service")
                        .system("system")
                        .build())
                .type(EventType.builder()
                        .name("name")
                        .version("1.0.0")
                        .build())
                .build();
    }

    private void saveErrorWithOldData() {
        EventMetadata metadata = getEventMetadata("old-error-event-id-1");
        CausingEvent causingEvent = saveCausingEvent(metadata);

        // TO DELETE
        storeError(causingEvent, ErrorState.TEMPORARY_RETRIED, ZonedDateTime.now().minusYears(2));
        storeError(causingEvent, ErrorState.PERMANENT_RETRIED, ZonedDateTime.now().minusYears(2));
        storeError(causingEvent, ErrorState.DELETED, ZonedDateTime.now().minusYears(2));
        storeError(causingEvent, ErrorState.PERMANENT, ZonedDateTime.now().minusYears(2));

        // AND NOT DELETE
        storeError(causingEvent, ErrorState.TEMPORARY_RETRY_PENDING, ZonedDateTime.now().minusYears(2));
        storeError(causingEvent, ErrorState.SEND_TO_MANUALTASK, ZonedDateTime.now().minusYears(2));
        storeError(causingEvent, ErrorState.DELETE_ON_MANUALTASK, ZonedDateTime.now().minusYears(2));
        storeError(causingEvent, ErrorState.RESOLVE_ON_MANUALTASK, ZonedDateTime.now().minusYears(2));
    }

    private void storeError(CausingEvent causingEvent, ErrorState temporaryRetried, ZonedDateTime zonedDateTime) {
        Error error = Error.builder()
                .state(temporaryRetried)
                .causingEvent(causingEvent)
                .errorEventData(ErrorEventData.builder()
                        .code("errorCode1")
                        .temporality(ErrorEventData.Temporality.PERMANENT)
                        .message("test")
                        .stackTrace("test-stack-trace")
                        .stackTraceHash("test-stack-trace-hash")
                        .build())
                .errorEventMetadata(getEventMetadata(UUID.randomUUID().toString()))
                .closingReason("")
                .created(zonedDateTime)
                .build();
        errorRepository.save(error);
    }
}
