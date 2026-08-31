package ch.admin.bit.jeap.errorhandling.domain.error;

import ch.admin.bit.jeap.errorhandling.domain.audit.AuditLogService;
import ch.admin.bit.jeap.errorhandling.domain.group.ErrorGroupConfigProperties;
import ch.admin.bit.jeap.errorhandling.domain.group.ErrorGroupService;
import ch.admin.bit.jeap.errorhandling.domain.manualtask.taskFactory.TaskFactory;
import ch.admin.bit.jeap.errorhandling.domain.metrics.ErrorHandlingMetricsService;
import ch.admin.bit.jeap.errorhandling.domain.resend.scheduler.ScheduledResendService;
import ch.admin.bit.jeap.errorhandling.domain.resend.strategy.ResendingStrategy;
import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.FailedEventResender;
import ch.admin.bit.jeap.errorhandling.infrastructure.manualtask.TaskManagementClient;
import ch.admin.bit.jeap.errorhandling.infrastructure.manualtask.TaskManagementException;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.CausingEvent;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error.ErrorState;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.mockito.Mockito.*;


@ExtendWith({MockitoExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
class DeleteTest {
    private static final UUID errorId = UUID.randomUUID();
    private static final UUID taskId = UUID.randomUUID();
    @Mock
    private ErrorGroupConfigProperties errorGroupConfigProperties;
    @Mock
    private ErrorRepository errorRepository;
    @Mock
    private ErrorGroupService errorGroupService;
    @Mock
    private ErrorHandlingMetricsService errorHandlingMetricsService;
    @Mock
    private ScheduledResendService scheduledResendService;
    @Mock
    private FailedEventResender failedEventResender;
    @Mock
    private TaskManagementClient taskManagementClient;
    @Mock
    private ResendingStrategy resendingStrategy;
    @Mock
    private ErrorFactory errorFactory;
    @Mock
    private TaskFactory taskFactory;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private Error error;
    @Mock
    private CausingEvent causingEvent;
    private ErrorState state;
    private ErrorService target;


    @BeforeEach
    void setup() {
        target = new ErrorService(errorRepository,
                scheduledResendService,
                failedEventResender,
                taskManagementClient,
                taskFactory,
                resendingStrategy,
                errorFactory,
                errorHandlingMetricsService,
                auditLogService,
                errorGroupService);

        when(errorRepository.getReferenceById(errorId)).thenReturn(error);
        when(error.getState()).then(a -> state);
        doAnswer(a -> state = a.getArgument(0)).when(error).setState(any());
        lenient().when(error.getManualTaskId()).thenReturn(taskId);
    }

    @Test
    void deleteTemporary() {
        state = ErrorState.TEMPORARY_RETRY_PENDING;

        target.delete(errorId, "");

        Assertions.assertEquals(ErrorState.DELETED, state);
        verify(scheduledResendService).cancelScheduledResends(error);
        verify(auditLogService).logDeleteError(error);
        verifyNoMoreInteractions(scheduledResendService, failedEventResender, taskManagementClient, auditLogService, errorGroupService);
    }

    @Test
    void deletePermanent() throws TaskManagementException {
        state = ErrorState.PERMANENT;

        target.delete(errorId, "");

        Assertions.assertEquals(ErrorState.DELETED, state);
        verify(taskManagementClient).closeTask(taskId);
        verify(auditLogService).logDeleteError(error);
        verifyNoMoreInteractions(scheduledResendService, failedEventResender, taskManagementClient, auditLogService, errorGroupService);
    }

    @Test
    void deletePermanentFailedOnManualTask() throws TaskManagementException {
        state = ErrorState.PERMANENT;
        doThrow(mock(TaskManagementException.class)).when(taskManagementClient).closeTask(taskId);

        target.delete(errorId, "");

        Assertions.assertEquals(ErrorState.DELETE_ON_MANUALTASK, state);
        verify(taskManagementClient).closeTask(taskId);
        verify(auditLogService).logDeleteError(error);
        verifyNoMoreInteractions(scheduledResendService, failedEventResender, taskManagementClient, auditLogService, errorGroupService);
    }

    @Test
    void deletePermanentModulithDefersManualTaskClose() throws TaskManagementException {
        state = ErrorState.PERMANENT;
        when(error.getCausingEvent()).thenReturn(causingEvent);
        when(causingEvent.getOrigin()).thenReturn(CausingEvent.Origin.MODULITH_PUBLICATION);

        target.delete(errorId, "resolved manually");

        Assertions.assertEquals(ErrorState.DELETE_ON_MANUALTASK, state);
        InOrder ordering = inOrder(failedEventResender, error);
        ordering.verify(failedEventResender).discardIfModulith(error, "resolved manually");
        ordering.verify(error).setState(ErrorState.DELETE_ON_MANUALTASK);
        verify(taskManagementClient, never()).closeTask(any());
        verify(auditLogService).logDeleteError(error);
    }

    @Test
    void deletePermanentModulithCommandFailureLeavesErrorOpen() {
        state = ErrorState.PERMANENT;
        when(error.getCausingEvent()).thenReturn(causingEvent);
        when(causingEvent.getOrigin()).thenReturn(CausingEvent.Origin.MODULITH_PUBLICATION);
        doThrow(new IllegalStateException("outbox failed")).when(failedEventResender)
                .discardIfModulith(error, "resolved manually");

        Assertions.assertThrows(IllegalStateException.class,
                () -> target.delete(errorId, "resolved manually"));

        Assertions.assertEquals(ErrorState.PERMANENT, state);
        verify(error, never()).setState(any());
        verify(taskManagementClient, never()).closeTask(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void deleteSendToManualTaskService() throws TaskManagementException {
        state = ErrorState.SEND_TO_MANUALTASK;
        lenient().doThrow(mock(TaskManagementException.class)).when(taskManagementClient).closeTask(taskId);

        target.delete(errorId, "");

        Assertions.assertEquals(ErrorState.DELETED, state);
        verify(auditLogService).logDeleteError(error);
        verifyNoMoreInteractions(scheduledResendService, failedEventResender, taskManagementClient, auditLogService, errorGroupService);
    }

    @Test
    void deleteDeleted() {
        state = ErrorState.DELETED;

        Assertions.assertThrows(IllegalStateException.class, () -> target.delete(errorId, ""));

        Assertions.assertEquals(ErrorState.DELETED, state);
        verifyNoMoreInteractions(scheduledResendService, failedEventResender, taskManagementClient, auditLogService, errorGroupService);
    }
}
