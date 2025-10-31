package ch.admin.bit.jeap.errorhandling.infrastructure.persistence;

import ch.admin.bit.jeap.errorhandling.domain.housekeeping.RepositoryHousekeeping;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error.ErrorState;
import ch.admin.bit.jeap.errorhandling.web.api.ErrorGroupListSearchCriteria;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
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
    @Autowired
    private ErrorGroupRepository errorGroupRepository;

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
        Error error = errorRepository.findAll().getFirst();
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
        assertEquals("service", eventSources.getFirst());
    }

    @Test
    void getAllErrorCodes() {
        List<String> errorCodes = errorRepository.getAllErrorCodes();
        assertEquals(1, errorCodes.size());
        assertEquals("errorCode1", errorCodes.getFirst());
    }

    @Test
    void findByGroupId_returnsErrorsWithGivenGroupId() {
        EventMetadata metadata = getEventMetadata("event-id-group");
        CausingEvent causingEvent = saveCausingEvent(metadata);

        ErrorGroup errorGroup = new ErrorGroup("group-error-code", "group-event-name", "group-error-publisher", "group-error-message", "group-error-stack-trace-hash");
        errorGroupRepository.save(errorGroup);

        Error error = Error.builder()
                .state(Error.ErrorState.PERMANENT)
                .causingEvent(causingEvent)
                .errorGroup(errorGroup)
                .errorEventData(ErrorEventData.builder()
                        .code("errorCode1")
                        .temporality(ErrorEventData.Temporality.PERMANENT)
                        .message("test")
                        .stackTrace("test-stack-trace")
                        .stackTraceHash("test-stack-trace-hash")
                        .build())
                .errorEventMetadata(getEventMetadata(UUID.randomUUID().toString()))
                .closingReason("")
                .created(ZonedDateTime.now())
                .build();
        errorRepository.save(error);

        Page<Error> result = errorRepository.findByGroupIdAndCriteria(errorGroup.getId(), null, Pageable.ofSize(10));
        assertThat(result.getContent()).hasSize(1);
        assertEquals(errorGroup.getId(), result.getContent().getFirst().getErrorGroup().getId());
    }

    @Test
    void findByGroupId_returnsErrorsWithGivenGroupIdAndCriteria() {
        EventMetadata metadata = getEventMetadata("event-id-group");
        CausingEvent causingEvent = saveCausingEvent(metadata);

        ErrorGroup errorGroup = new ErrorGroup("group-error-code", "group-event-name", "group-error-publisher", "group-error-message", "group-error-stack-trace-hash");
        errorGroupRepository.save(errorGroup);

        Error error = Error.builder()
                .state(Error.ErrorState.PERMANENT)
                .causingEvent(causingEvent)
                .errorGroup(errorGroup)
                .errorEventData(ErrorEventData.builder()
                        .code("errorCode1")
                        .temporality(ErrorEventData.Temporality.PERMANENT)
                        .message("test")
                        .stackTrace("test-stack-trace")
                        .stackTraceHash("test-stack-trace-hash")
                        .build())
                .errorEventMetadata(getEventMetadata(UUID.randomUUID().toString()))
                .closingReason("")
                .created(ZonedDateTime.now())
                .build();
        errorRepository.save(error);

        ErrorGroupListSearchCriteria criteria = ErrorGroupListSearchCriteria.builder()
                .dateFrom(ZonedDateTime.now().minusDays(1))
                .dateTo(ZonedDateTime.now().plusDays(1))
                .build();

        Page<Error> result = errorRepository.findByGroupIdAndCriteria(errorGroup.getId(), criteria, Pageable.ofSize(10));
        assertThat(result.getContent()).hasSize(1);
        assertEquals(errorGroup.getId(), result.getContent().getFirst().getErrorGroup().getId());
    }

    @Test
    void findByGroupIdWithNotExistingGroupId_returnsEmpty() {
        Page<Error> emptyResult = errorRepository.findByGroupIdAndCriteria(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), null, Pageable.ofSize(10));
        assertThat(emptyResult.getContent()).isEmpty();
    }

    @Test
    void countOpenErrorsByStateAndClusterName() {
        errorRepository.deleteAll();

        // Setup: Create errors with different cluster names and states
        EventMetadata metadata1 = getEventMetadata("event-cluster-test-1");
        EventMetadata metadata2 = getEventMetadata("event-cluster-test-2");
        EventMetadata metadata3 = getEventMetadata("event-cluster-test-3");

        CausingEvent causingEventClusterA = saveCausingEventWithCluster(metadata1, "cluster-a");
        CausingEvent causingEventClusterB = saveCausingEventWithCluster(metadata2, "cluster-b");
        CausingEvent causingEventClusterC = saveCausingEventWithCluster(metadata3, null); // Test null cluster

        // cluster-a: 2 PERMANENT, 1 TEMPORARY_RETRY_PENDING, 1 SEND_TO_MANUALTASK = 4 total
        saveError(ErrorState.PERMANENT, causingEventClusterA);
        saveError(ErrorState.PERMANENT, causingEventClusterA);
        saveError(ErrorState.TEMPORARY_RETRY_PENDING, causingEventClusterA);
        saveError(ErrorState.SEND_TO_MANUALTASK, causingEventClusterA);
        // These should NOT be counted:
        saveError(ErrorState.DELETED, causingEventClusterA);
        saveError(ErrorState.TEMPORARY_RETRIED, causingEventClusterA);

        // cluster-b: 3 PERMANENT, 2 SEND_TO_MANUALTASK = 5 total
        saveError(ErrorState.PERMANENT, causingEventClusterB);
        saveError(ErrorState.PERMANENT, causingEventClusterB);
        saveError(ErrorState.PERMANENT, causingEventClusterB);
        saveError(ErrorState.SEND_TO_MANUALTASK, causingEventClusterB);
        saveError(ErrorState.SEND_TO_MANUALTASK, causingEventClusterB);
        // These should NOT be counted:
        saveError(ErrorState.RESOLVE_ON_MANUALTASK, causingEventClusterB);

        // null cluster: 1 TEMPORARY_RETRY_PENDING = 1 total
        saveError(ErrorState.TEMPORARY_RETRY_PENDING, causingEventClusterC);
        saveError(ErrorState.DELETE_ON_MANUALTASK, causingEventClusterC); // should NOT be counted

        // Execute
        List<ErrorCountByClusterNameResult> results = errorRepository.countOpenErrorsByStateAndClusterName();

        // Verify
        assertThat(results).hasSize(3);

        ErrorCountByClusterNameResult clusterAResult = results.stream()
                .filter(r -> "cluster-a".equals(r.clusterName()))
                .findFirst()
                .orElseThrow();
        assertThat(clusterAResult.errorCount()).isEqualTo(4L);

        ErrorCountByClusterNameResult clusterBResult = results.stream()
                .filter(r -> "cluster-b".equals(r.clusterName()))
                .findFirst()
                .orElseThrow();
        assertThat(clusterBResult.errorCount()).isEqualTo(5L);

        ErrorCountByClusterNameResult nullClusterResult = results.stream()
                .filter(r -> r.clusterName() == null)
                .findFirst()
                .orElseThrow();
        assertThat(nullClusterResult.errorCount()).isEqualTo(1L);
    }

    @NotNull
    private CausingEvent saveCausingEventWithCluster(EventMetadata metadata, String clusterName) {
        CausingEvent causingEvent = CausingEvent.builder()
                .message(EventMessage.builder()
                        .offset(1)
                        .payload("test".getBytes(StandardCharsets.UTF_8))
                        .topic("topic")
                        .clusterName(clusterName)
                        .build())
                .metadata(metadata)
                .build();
        causingEventRepository.save(causingEvent);
        return causingEvent;
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
