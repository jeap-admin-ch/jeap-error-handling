package ch.admin.bit.jeap.errorhandling.domain.error;

import ch.admin.bit.jeap.errorhandling.ErrorStubs;
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
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error.ErrorState;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorGroupRepository;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorRepository;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ScheduledResend;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

@ActiveProfiles("scheduled-resend-test")
@SpringBootTest
@ExtendWith(MockitoExtension.class)
@Import(ErrorService.class)
class ScheduledResendTest {
    private final static UUID errorId = UUID.randomUUID();
    private final static UUID newErrorId = UUID.randomUUID();
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
    private TaskFactory taskFactory;
    @Mock
    private ScheduledResend scheduledResend;
    @MockBean
    private AuditLogService auditLogService;
    @Mock
    private TaskDto taskDto;
    @Captor
    private ArgumentCaptor<Error> newErrorCapture;
    private final UUID taskId = UUID.randomUUID();
    private Error error;

    @Configuration
    @Profile("scheduled-resend-test")
    static class TestConfig {
        @Bean
        ErrorFactory errorFactory() {
            return new ErrorFactory() {
                @Override
                UUID randomErrorId() {
                    return newErrorId;
                }
            };
        }
    }

    @SuppressWarnings("SpringJavaAutowiredMembersInspection")
    @Autowired
    private ErrorService target;

    @BeforeEach
    void setup() {
        when(scheduledResend.getErrorId()).thenReturn(errorId);
        error = ErrorStubs.createTemporaryError();
        when(errorRepository.getReferenceById(errorId)).thenReturn(error);
    }

    @Test
    void scheduledResend() {
        target.scheduledResend(scheduledResend);

        assertEquals(ErrorState.TEMPORARY_RETRIED, error.getState());
        verify(scheduledResendService).setResent(scheduledResend);
        verify(failedEventResender).resend(error);
        verify(errorRepository, atLeastOnce()).getReferenceById(errorId);
        verifyNoMoreInteractions(errorRepository, scheduledResendService, failedEventResender, taskManagementClient);
    }

    @Test
    void scheduledResendFails_createPermanentError() {
        doReturn(taskId).when(taskDto).getId();
        doReturn(taskDto).when(taskFactory).create(any());
        doThrow(new RuntimeException()).when(failedEventResender).resend(error);

        target.scheduledResend(scheduledResend);

        assertEquals(ErrorState.TEMPORARY_RETRIED, error.getState());
        verify(scheduledResendService).setResent(scheduledResend);
        verify(failedEventResender).resend(error);
        verify(taskManagementClient).createTask(taskDto);
        verify(errorRepository, atLeastOnce()).getReferenceById(errorId);
        verify(errorRepository, atLeastOnce()).countErrorsForCausingEvent(error.getCausingEventMetadata().getId());
        verify(errorRepository, atLeastOnce()).save(newErrorCapture.capture());
        verifyNoMoreInteractions(errorRepository, scheduledResendService, failedEventResender, taskManagementClient);
        assertEquals(newErrorId, newErrorCapture.getValue().getId());
        assertEquals(taskId, newErrorCapture.getValue().getManualTaskId());
        assertEquals(ErrorState.PERMANENT, newErrorCapture.getValue().getState());
    }

    @Test
    void scheduledResendFails_createTemporaryError() {
        doThrow(new RuntimeException()).when(failedEventResender).resend(error);
        doReturn(Optional.of(ZonedDateTime.now())).when(resendingStrategy).determineResend(anyInt(), any(), any(), any(), any());
        doReturn(error).when(errorRepository).save(error);

        target.scheduledResend(scheduledResend);

        assertEquals(ErrorState.TEMPORARY_RETRIED, error.getState());
        verify(scheduledResendService).setResent(scheduledResend);
        verify(scheduledResendService).scheduleResend(eq(newErrorId), any());
        verify(failedEventResender).resend(error);
        verify(errorRepository, atLeastOnce()).getReferenceById(errorId);
        verify(errorRepository, atLeastOnce()).countErrorsForCausingEvent(error.getCausingEventMetadata().getId());
        verify(errorRepository, atLeastOnce()).save(newErrorCapture.capture());
        verifyNoMoreInteractions(errorRepository, scheduledResendService, failedEventResender, taskManagementClient);
        assertEquals(newErrorId, newErrorCapture.getValue().getId());
        assertNull(newErrorCapture.getValue().getManualTaskId());
        assertEquals(ErrorState.TEMPORARY_RETRY_PENDING, newErrorCapture.getValue().getState());
    }
}
