package ch.admin.bit.jeap.errorhandling.domain.error;

import ch.admin.bit.jeap.errorhandling.domain.audit.AuditLogService;
import ch.admin.bit.jeap.errorhandling.domain.group.ErrorGroupConfigProperties;
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
class SyncWithManualTasksTest {
    private final static UUID taskId = UUID.randomUUID();
    @MockBean
    private ErrorGroupConfigProperties errorGroupConfigProperties;
    @MockBean
    private ErrorRepository errorRepository;
    @MockBean
    private ErrorGroupService errorGroupService;
    @MockBean
    private ErrorGroupRepository errorGroupRepository;
    @MockBean
    private ErrorHandlingMetricsService errorHandlingMetricsService;
    @MockBean
    private ScheduledResendService scheduledResendService;
    @MockBean
    private KafkaFailedEventResender failedEventResender;
    @MockBean
    private TaskManagementClient taskManagementClient;
    @MockBean
    private ResendingStrategy resendingStrategy;
    @MockBean
    private ErrorFactory errorFactory;
    @MockBean
    private TaskFactory taskFactory;
    @MockBean
    private AuditLogService auditLogService;
    @Mock(lenient = true)
    TaskDto taskDto;
    @Mock(lenient = true)
    private Error error;
    private ErrorState state;
    @SuppressWarnings("SpringJavaAutowiredMembersInspection")
    @Autowired
    private ErrorService target;

    @BeforeEach
    void setup() {
        when(error.getState()).then(a -> state);
        when(error.getManualTaskId()).thenReturn(taskId);
        when(taskFactory.create(error)).thenReturn(taskDto);
        when(taskDto.getId()).thenReturn(taskId);
        doAnswer(a -> state = a.getArgument(0)).when(error).setState(any());
    }

    @Test
    void createManualTask() throws TaskManagementException {
        state = ErrorState.SEND_TO_MANUALTASK;

        target.createManualTask(error);

        Assertions.assertEquals(ErrorState.PERMANENT, state);
        verify(taskManagementClient).createTask(taskDto);
        verify(error).setManualTaskId(taskId);
        verifyNoMoreInteractions(scheduledResendService, failedEventResender, taskManagementClient);
    }

    @Test
    void createManualTaskFailed() throws TaskManagementException {
        state = ErrorState.SEND_TO_MANUALTASK;
        doThrow(mock(TaskManagementException.class)).when(taskManagementClient).createTask(taskDto);

        target.createManualTask(error);

        Assertions.assertEquals(ErrorState.SEND_TO_MANUALTASK, state);
        verify(taskManagementClient).createTask(taskDto);
        verifyNoMoreInteractions(scheduledResendService, failedEventResender, taskManagementClient);
    }

    @Test
    void deleteManualTask() throws TaskManagementException {
        state = ErrorState.DELETE_ON_MANUALTASK;

        target.deleteManualTask(error);

        Assertions.assertEquals(ErrorState.DELETED, state);
        verify(taskManagementClient).closeTask(taskId);
        verifyNoMoreInteractions(scheduledResendService, failedEventResender, taskManagementClient);
    }

    @Test
    void deleteManualTaskFailed() throws TaskManagementException {
        state = ErrorState.DELETE_ON_MANUALTASK;
        doThrow(mock(TaskManagementException.class)).when(taskManagementClient).closeTask(taskId);

        target.deleteManualTask(error);

        Assertions.assertEquals(ErrorState.DELETE_ON_MANUALTASK, state);
        verify(taskManagementClient).closeTask(taskId);
        verifyNoMoreInteractions(scheduledResendService, failedEventResender, taskManagementClient);
    }

    @Test
    void closeManualTask() throws TaskManagementException {
        state = ErrorState.RESOLVE_ON_MANUALTASK;

        target.closeManualTask(error);

        Assertions.assertEquals(ErrorState.PERMANENT_RETRIED, state);
        verify(taskManagementClient).closeTask(taskId);
        verifyNoMoreInteractions(scheduledResendService, failedEventResender, taskManagementClient);
    }


    @Test
    void closeManualTaskFailed() throws TaskManagementException {
        state = ErrorState.RESOLVE_ON_MANUALTASK;
        doThrow(mock(TaskManagementException.class)).when(taskManagementClient).closeTask(taskId);

        target.closeManualTask(error);

        Assertions.assertEquals(ErrorState.RESOLVE_ON_MANUALTASK, state);
        verify(taskManagementClient).closeTask(taskId);
        verifyNoMoreInteractions(scheduledResendService, failedEventResender, taskManagementClient);
    }
}
