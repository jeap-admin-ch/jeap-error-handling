package ch.admin.bit.jeap.errorhandling.domain.group;

import ch.admin.bit.jeap.errorhandling.domain.exceptions.ErrorGroupNotFoundException;
import ch.admin.bit.jeap.errorhandling.domain.exceptions.TicketNumberAlreadyExistsException;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorEventData;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorGroup;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorGroupRepository;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.EventMetadata;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.EventPublisher;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.EventType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class ErrorGroupServiceTest {

    @Test
    void testAssignToErrorGroup_WhenGroupingDisabled_ThenNoGroup() {
        Error error = mockError("test-service", "test-event", "test-code", "test-stack-trace-hash", "test-stack-trace", "test-error-message");
        ErrorGroupRepository errorGroupRepository = mockErrorGroupRepository();
        ErrorGroupService errorGroupService = createErrorGroupService(errorGroupRepository, false);

        ErrorGroup errorGroupAssigned = errorGroupService.assignToErrorGroup(error);

        assertThat(errorGroupAssigned).isNull();
        Mockito.verifyNoInteractions(errorGroupRepository);
        Mockito.verify(error, never()).setErrorGroup(any());
    }

    @Test
    void testAssignToErrorGroup_createNewGroup() {
        Error error = mockError("test-service", "test-event", "test-code", "test-stack-trace-hash", "test-stack-trace", "test-error-message");
        ErrorGroupRepository errorGroupRepository = mockErrorGroupRepository();
        ErrorGroupService errorGroupService = createErrorGroupService(errorGroupRepository, true);

        ErrorGroup errorGroupAssigned = errorGroupService.assignToErrorGroup(error);

        Mockito.verify(error).setErrorGroup(errorGroupAssigned);
        Mockito.verify(errorGroupRepository).save(errorGroupAssigned);
        assertThat(errorGroupAssigned).isNotNull();
        assertThat(errorGroupAssigned.getErrorPublisher()).isEqualTo("test-service");
        assertThat(errorGroupAssigned.getEventName()).isEqualTo("test-event");
        assertThat(errorGroupAssigned.getErrorCode()).isEqualTo("test-code");
        assertThat(errorGroupAssigned.getErrorStackTraceHash()).isEqualTo("test-stack-trace-hash");
    }

    @Test
    void testAssignToErrorGroup_addToExistingGroup() {
        Error error = mockError("test-service", "test-event", "test-code", "test-stack-trace-hash", "test-stack-trace", "test-error-message");
        ErrorGroup errorGroup = ErrorGroup.from(error);
        ErrorGroupRepository errorGroupRepository = mockErrorGroupRepository();
        when(errorGroupRepository.findByErrorPublisherAndErrorCodeAndEventNameAndErrorStackTraceHash("test-service", "test-code", "test-event", "test-stack-trace-hash")).
                thenReturn(Optional.of(errorGroup));
        ErrorGroupService errorGroupService = createErrorGroupService(errorGroupRepository, true);

        ErrorGroup errorGroupAssigned = errorGroupService.assignToErrorGroup(error);

        assertThat(errorGroupAssigned).isSameAs(errorGroup);
        Mockito.verify(error).setErrorGroup(errorGroup);
        Mockito.verify(errorGroupRepository, never()).save(errorGroup);
    }

    @Test
    void testAssignToErrorGroup_WhenNoStackTraceHash_ThenNoGroup() {
        Error error = mockError("test-service", "test-event", "test-code", null, null, "test-error-message");
        ErrorGroupRepository errorGroupRepository = mockErrorGroupRepository();
        ErrorGroupService errorGroupService = createErrorGroupService(errorGroupRepository, true);

        ErrorGroup errorGroupAssigned = errorGroupService.assignToErrorGroup(error);

        assertThat(errorGroupAssigned).isNull();
        Mockito.verifyNoInteractions(errorGroupRepository);
        Mockito.verify(error, never()).setErrorGroup(any());
    }

    @Test
    void testUpdateFreeText() {
        Error error = mockError("test-service", "test-event", "test-code", "test-stack-trace-hash", "test-stack-trace", "test-error-message");
        ErrorGroup errorGroup = ErrorGroup.from(error);
        ErrorGroupRepository errorGroupRepository = mockErrorGroupRepository();
        when(errorGroupRepository.findById(errorGroup.getId())).thenReturn(Optional.of(errorGroup));
        ErrorGroupService errorGroupService = createErrorGroupService(errorGroupRepository, false);
        final String freeText = "test-free-text";

        ErrorGroup errorGroupUpdated = errorGroupService.updateFreeText(errorGroup.getId(), freeText);

        assertThat(errorGroupUpdated.getFreeText()).isEqualTo(freeText);
    }

    @Test
    void testUpdateFreeText_ErrorGroupNotFound() {
        ErrorGroupRepository errorGroupRepository = mockErrorGroupRepository();
        when(errorGroupRepository.findById(any())).thenReturn(Optional.empty());
        ErrorGroupService errorGroupService = createErrorGroupService(errorGroupRepository, false);

        assertThatThrownBy(() -> errorGroupService.updateFreeText(UUID.randomUUID(), "test-free-text")).
                isInstanceOf(ErrorGroupNotFoundException.class);
    }

    @Test
    void testUpdateTicketNumber() {
        Error error = mockError("test-service", "test-event", "test-code", "test-stack-trace-hash", "test-stack-trace", "test-error-message");
        ErrorGroup errorGroup = ErrorGroup.from(error);
        ErrorGroupRepository errorGroupRepository = mockErrorGroupRepository();
        when(errorGroupRepository.findById(errorGroup.getId())).thenReturn(Optional.of(errorGroup));
        ErrorGroupService errorGroupService = createErrorGroupService(errorGroupRepository, false);
        final String ticketNumber = "test-ticket-number";

        ErrorGroup errorGroupUpdated = errorGroupService.updateTicketNumber(errorGroup.getId(), ticketNumber);

        assertThat(errorGroupUpdated.getTicketNumber()).isEqualTo(ticketNumber);
    }

    @Test
    void testUpdateTicketNumber_ErrorGroupNotFound() {
        ErrorGroupRepository errorGroupRepository = mockErrorGroupRepository();
        when(errorGroupRepository.findById(any())).thenReturn(Optional.empty());
        ErrorGroupService errorGroupService = createErrorGroupService(errorGroupRepository, false);

        assertThatThrownBy(() -> errorGroupService.updateTicketNumber(UUID.randomUUID(), "test-ticket-number")).
                isInstanceOf(ErrorGroupNotFoundException.class);
    }

    @Test
    void testUpdateTicketNumber_TicketNumberAlreadyAssigned() {
        Error error = mockError("test-service", "test-event", "test-code", "test-stack-trace-hash", "test-stack-trace", "test-error-message");
        ErrorGroup errorGroup = ErrorGroup.from(error);
        ErrorGroupRepository errorGroupRepository = mockErrorGroupRepository();
        when(errorGroupRepository.findById(errorGroup.getId())).thenReturn(Optional.of(errorGroup));
        final String ticketNumber = "test-ticket-number";
        when(errorGroupRepository.existsByTicketNumber(ticketNumber)).thenReturn(true);
        ErrorGroupService errorGroupService = createErrorGroupService(errorGroupRepository, false);

        assertThatThrownBy(() -> errorGroupService.updateTicketNumber(errorGroup.getId(), ticketNumber)).
                isInstanceOf(TicketNumberAlreadyExistsException.class);
    }

    @Test
    void testGetErrorGroupAggregatedData() {
        final UUID errorGroupId = UUID.randomUUID();
        final ErrorGroupAggregatedData errorGroupAggregatedData = mock(ErrorGroupAggregatedData.class);
        ErrorGroupRepository errorGroupRepository = mockErrorGroupRepository();
        when(errorGroupRepository.findErrorGroupAggregatedData(errorGroupId)).thenReturn(Optional.of(errorGroupAggregatedData));
        ErrorGroupService errorGroupService = createErrorGroupService(errorGroupRepository, false);

        ErrorGroupAggregatedData result = errorGroupService.getErrorGroupAggregatedData(errorGroupId);

        assertThat(result).isSameAs(errorGroupAggregatedData);
    }

    @Test
    void testGetErrorGroupAggregatedData_ErrorGroupNotFound() {
        final UUID errorGroupId = UUID.randomUUID();
        ErrorGroupRepository errorGroupRepository = mockErrorGroupRepository();
        when(errorGroupRepository.findErrorGroupAggregatedData(errorGroupId)).thenReturn(Optional.empty());
        ErrorGroupService errorGroupService = createErrorGroupService(errorGroupRepository, false);

        assertThatThrownBy(() -> errorGroupService.getErrorGroupAggregatedData(errorGroupId)).
                isInstanceOf(ErrorGroupNotFoundException.class);
    }

    @Test
    void testFindErrorGroupAggregatedData() {
        final UUID errorGroupId = UUID.randomUUID();
        final ErrorGroupAggregatedData errorGroupAggregatedData = mock(ErrorGroupAggregatedData.class);
        ErrorGroupRepository errorGroupRepository = mockErrorGroupRepository();
        when(errorGroupRepository.findErrorGroupAggregatedData(errorGroupId)).thenReturn(Optional.of(errorGroupAggregatedData));
        ErrorGroupService errorGroupService = createErrorGroupService(errorGroupRepository, false);

        ErrorGroupAggregatedData result = errorGroupService.getErrorGroupAggregatedData(errorGroupId);

        assertThat(result).isSameAs(errorGroupAggregatedData);
    }

    private ErrorGroupService createErrorGroupService(ErrorGroupRepository errorGroupRepository, boolean errorGroupingEnabled) {
        ErrorGroupConfigProperties errorGroupConfigProperties = new ErrorGroupConfigProperties();
        errorGroupConfigProperties.setErrorGroupingEnabled(errorGroupingEnabled);
        return new ErrorGroupService(errorGroupConfigProperties, errorGroupRepository, mock(PlatformTransactionManager.class));
    }

    private ErrorGroupRepository mockErrorGroupRepository() {
        ErrorGroupRepository errorGroupRepository = mock(ErrorGroupRepository.class);
        when(errorGroupRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return errorGroupRepository;
    }

    @SuppressWarnings("SameParameterValue")
    private Error mockError(String publisherName, String eventTypeName, String errorCode, String stackTraceHash, String stackTrace, String errorMessage) {
        Error error = mock(Error.class);
        EventMetadata causingEventMetadata = mock(EventMetadata.class);
        when(error.getCausingEventMetadata()).thenReturn(causingEventMetadata);
        EventType causingEventType = mock(EventType.class);
        when(causingEventMetadata.getType()).thenReturn(causingEventType);
        when(causingEventType.getName()).thenReturn(eventTypeName);
        ErrorEventData errorEventData = mock(ErrorEventData.class);
        when(error.getErrorEventData()).thenReturn(errorEventData);
        EventMetadata eventMetadata = mock(EventMetadata.class);
        when(error.getErrorEventMetadata()).thenReturn(eventMetadata);
        EventPublisher publisher = mock(EventPublisher.class);
        when(eventMetadata.getPublisher()).thenReturn(publisher);
        when(publisher.getService()).thenReturn(publisherName);
        when(errorEventData.getCode()).thenReturn(errorCode);
        when(errorEventData.getStackTraceHash()).thenReturn(stackTraceHash);
        when(errorEventData.getStackTrace()).thenReturn(stackTrace);
        when(errorEventData.getMessage()).thenReturn(errorMessage);
        return error;
    }

}
