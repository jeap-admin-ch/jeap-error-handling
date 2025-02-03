package ch.admin.bit.jeap.errorhandling.domain.error;

import ch.admin.bit.jeap.errorhandling.domain.audit.AuditLogService;
import ch.admin.bit.jeap.errorhandling.domain.group.ErrorGroupConfigProperties;
import ch.admin.bit.jeap.errorhandling.domain.group.ErrorGroupService;
import ch.admin.bit.jeap.errorhandling.domain.manualtask.taskFactory.TaskFactory;
import ch.admin.bit.jeap.errorhandling.domain.metrics.ErrorHandlingMetricsService;
import ch.admin.bit.jeap.errorhandling.domain.resend.scheduler.ScheduledResendService;
import ch.admin.bit.jeap.errorhandling.domain.resend.strategy.ResendingStrategy;
import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.KafkaFailedEventResender;
import ch.admin.bit.jeap.errorhandling.infrastructure.manualtask.TaskManagementClient;
import ch.admin.bit.jeap.errorhandling.infrastructure.manualtask.TaskManagementException;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error.ErrorState;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorGroupRepository;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith({SpringExtension.class})
@ExtendWith({MockitoExtension.class})
@Import(ErrorService.class)
class ManualResendTest {
    private final static UUID errorId = UUID.randomUUID();
    private final static UUID taskId = UUID.randomUUID();
    @MockBean
    private ErrorGroupConfigProperties errorGroupConfigProperties;
    @MockBean
    private ErrorRepository errorRepository;
    @MockBean
    private ErrorGroupRepository errorGroupRepository;
    @MockBean
    private ErrorGroupService errorGroupService;
    @MockBean
    private ErrorHandlingMetricsService errorHandlingMetricsService;
    @MockBean
    private ScheduledResendService scheduledResendService;
    @MockBean
    private KafkaFailedEventResender failedEventResender;
    @MockBean
    private TaskManagementClient taskManagementClient;
    @MockBean
    private ErrorFactory errorFactory;
    @MockBean
    private ResendingStrategy resendingStrategy;
    @MockBean
    private TaskFactory taskFactory;
    @MockBean
    private AuditLogService auditLogService;
    @Mock(lenient = true)
    private Error error;
    private ErrorState state;
    @SuppressWarnings("SpringJavaAutowiredMembersInspection")
    @Autowired
    private ErrorService target;

    @BeforeEach
    void setup() {
        when(errorRepository.getReferenceById(errorId)).thenReturn(error);
        when(error.getState()).then(a -> state);
        doAnswer(a -> state = a.getArgument(0)).when(error).setState(any());
        when(error.getManualTaskId()).thenReturn(taskId);
    }

    @Test
    void manualResendTemporary() {
        state = ErrorState.TEMPORARY_RETRY_PENDING;

        target.manualResend(errorId);

        Assertions.assertEquals(ErrorState.TEMPORARY_RETRIED, state);
        verify(scheduledResendService).cancelScheduledResends(error);
        verify(failedEventResender).resend(error);
        verify(auditLogService).logResendCausingEvent(error);
        verifyNoMoreInteractions(scheduledResendService, failedEventResender, taskManagementClient, auditLogService);
    }

    @Test
    void manualResendPermanent() throws TaskManagementException {
        state = ErrorState.PERMANENT;

        target.manualResend(errorId);

        Assertions.assertEquals(ErrorState.PERMANENT_RETRIED, state);
        verify(taskManagementClient).closeTask(taskId);
        verify(failedEventResender).resend(error);
        verify(auditLogService).logResendCausingEvent(error);
        verifyNoMoreInteractions(scheduledResendService, failedEventResender, taskManagementClient, auditLogService);
    }

    @Test
    void manualResendSendToManualTask() {
        state = ErrorState.SEND_TO_MANUALTASK;

        target.manualResend(errorId);

        Assertions.assertEquals(ErrorState.PERMANENT_RETRIED, state);
        verify(failedEventResender).resend(error);
        verify(auditLogService).logResendCausingEvent(error);
        verifyNoMoreInteractions(scheduledResendService, failedEventResender, taskManagementClient, auditLogService);
    }

    @Test
    void manualResendPermanentFailedManualTask() throws TaskManagementException {
        state = ErrorState.PERMANENT;
        doThrow(mock(TaskManagementException.class)).when(taskManagementClient).closeTask(taskId);

        target.manualResend(errorId);

        Assertions.assertEquals(ErrorState.RESOLVE_ON_MANUALTASK, state);
        verify(taskManagementClient).closeTask(taskId);
        verify(failedEventResender).resend(error);
        verify(auditLogService).logResendCausingEvent(error);
        verifyNoMoreInteractions(scheduledResendService, failedEventResender, taskManagementClient, auditLogService);
    }

    @Test
    void manualResendAlreadyClosed() {
        state = ErrorState.TEMPORARY_RETRIED;

        Assertions.assertThrows(IllegalStateException.class, () -> target.manualResend(errorId));

        verify(error, never()).setState(any());
        verifyNoMoreInteractions(scheduledResendService, failedEventResender, taskManagementClient, auditLogService);
    }
}
