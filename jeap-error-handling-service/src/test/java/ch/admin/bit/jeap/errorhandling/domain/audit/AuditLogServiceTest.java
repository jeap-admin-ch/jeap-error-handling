package ch.admin.bit.jeap.errorhandling.domain.audit;

import ch.admin.bit.jeap.errorhandling.domain.user.UserService;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private Error error;

    @Test
    void test_whenUserUnauthenticated_thenThrowsException() {
        Mockito.when(userService.getAuthenticatedUser()).thenReturn(Optional.empty());
        AuditLogService auditLogService = new AuditLogService(userService, auditLogRepository);

        assertThatThrownBy(() -> auditLogService.logResendCausingEvent(error))
                .isInstanceOf(AuditLogException.class)
                .hasMessageContaining("The audit log requires an authenticated user.");

        assertThatThrownBy(() -> auditLogService.logDeleteError(error))
                .isInstanceOf(AuditLogException.class)
                .hasMessageContaining("The audit log requires an authenticated user.");
    }

}
