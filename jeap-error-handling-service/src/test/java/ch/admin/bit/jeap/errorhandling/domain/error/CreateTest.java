package ch.admin.bit.jeap.errorhandling.domain.error;

import ch.admin.bit.jeap.errorhandling.ErrorStubs;
import ch.admin.bit.jeap.errorhandling.domain.audit.AuditLogService;
import ch.admin.bit.jeap.errorhandling.domain.group.ErrorGroupService;
import ch.admin.bit.jeap.errorhandling.domain.manualtask.taskFactory.TaskFactory;
import ch.admin.bit.jeap.errorhandling.domain.metrics.ErrorHandlingMetricsService;
import ch.admin.bit.jeap.errorhandling.domain.resend.scheduler.ScheduledResendService;
import ch.admin.bit.jeap.errorhandling.domain.resend.strategy.ResendingStrategy;
import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.KafkaFailedEventResender;
import ch.admin.bit.jeap.errorhandling.infrastructure.manualtask.TaskDto;
import ch.admin.bit.jeap.errorhandling.infrastructure.manualtask.TaskManagementClient;
import ch.admin.bit.jeap.errorhandling.infrastructure.manualtask.TaskManagementException;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error.ErrorState;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorRepository;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.EventMetadata;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.EventPublisher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith({SpringExtension.class})
@ExtendWith({MockitoExtension.class})
@Import(ErrorService.class)
class CreateTest {
    private static final UUID errorId = UUID.randomUUID();
    private static final UUID taskId = UUID.randomUUID();
    private static final ZonedDateTime resentAt = ZonedDateTime.now();
    private static final String CAUSING_SERVICE = ErrorStubs.CAUSING_EVENT_PUBLISHER_SERVICE;
    @MockitoBean
    private ErrorRepository errorRepository;
    @MockitoBean
    private ErrorGroupService errorGroupService;
    @MockitoBean
    private ErrorHandlingMetricsService errorHandlingMetricsService;
    @MockitoBean
    private ScheduledResendService scheduledResendService;
    @MockitoBean
    private KafkaFailedEventResender failedEventResender;
    @MockitoBean
    private TaskManagementClient taskManagementClient;
    @MockitoBean
    private ResendingStrategy resendingStrategy;
    @MockitoBean
    private ErrorFactory errorFactory;
    @MockitoBean
    private TaskFactory taskFactory;
    @MockitoBean
    private AuditLogService auditLogService;
    @Mock(lenient = true)
    private TaskDto taskDto;
    @Mock(lenient = true)
    private Error error;
    @Mock(lenient = true)
    private EventMetadata eventMetadata;
    @Mock(lenient = true)
    private EventPublisher eventPublisher;
    private ErrorState state;
    @SuppressWarnings("SpringJavaAutowiredMembersInspection")
    @Autowired
    private ErrorService target;

    @BeforeEach
    void setup() {
        when(errorRepository.getReferenceById(errorId)).thenReturn(error);
        when(error.getId()).thenReturn(errorId);
        when(eventPublisher.getService()).thenReturn(CAUSING_SERVICE);
        when(eventMetadata.getPublisher()).thenReturn(eventPublisher);
        when(error.getErrorEventMetadata()).thenReturn(eventMetadata);
        when(error.getState()).then(a -> state);
        when(taskFactory.create(error)).thenReturn(taskDto);
        when(taskDto.getId()).thenReturn(taskId);
        doAnswer(a -> state = a.getArgument(0)).when(error).setState(any());
        when(errorRepository.save(any())).thenAnswer(a -> a.getArgument(0));
    }

    @Test
    void createPermanent() throws TaskManagementException {
        target.createPermanent(error);

        Assertions.assertEquals(ErrorState.PERMANENT, state);
        verify(taskManagementClient).createTask(taskDto);
        verify(error).setManualTaskId(taskId);
        verify(errorRepository, atLeastOnce()).save(error);
        verify(errorGroupService).assignToErrorGroup(error);
        verifyNoMoreInteractions(scheduledResendService, failedEventResender, taskManagementClient);
    }

    @Test
    void createPermanentSendToManualTaskFailed() throws TaskManagementException {
        doThrow(mock(TaskManagementException.class)).when(taskManagementClient).createTask(taskDto);

        target.createPermanent(error);

        Assertions.assertEquals(ErrorState.SEND_TO_MANUALTASK, state);
        verify(taskManagementClient).createTask(taskDto);
        verify(errorRepository).save(error);
        verify(errorGroupService).assignToErrorGroup(error);
        verifyNoMoreInteractions(scheduledResendService, failedEventResender, taskManagementClient);
    }

    @Test
    void createTemporary() {
        target.createTemporary(error, resentAt);

        Assertions.assertEquals(ErrorState.TEMPORARY_RETRY_PENDING, state);
        verify(errorRepository).save(error);
        verify(scheduledResendService).scheduleResend(errorId, resentAt);
        verifyNoMoreInteractions(scheduledResendService, failedEventResender, taskManagementClient, errorGroupService);
    }
}
