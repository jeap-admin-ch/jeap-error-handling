package ch.admin.bit.jeap.errorhandling.domain.audit;

import ch.admin.bit.jeap.errorhandling.domain.user.UserService;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.AuditLog;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.AuditLog.AuditedAction;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.AuditLogRepository;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final UserService userService;
    private final AuditLogRepository auditLogRepository;

    public void logResendCausingEvent(Error error) {
        logAction(error.getId(), AuditedAction.RESEND_CAUSING_EVENT);
    }

    public void logDeleteError(Error error) {
        logAction(error.getId(), AuditedAction.DELETE_ERROR);
    }

    public List<AuditLog> getAuditLogs(UUID errorId) {
        return auditLogRepository.findAllByErrorId(errorId);
    }

    private void logAction(UUID errorId, AuditedAction action) {
        User user = userService.getAuthenticatedUser()
                .orElseThrow(AuditLogException::noAuthenticatedUserException);
        AuditLog auditLog = AuditLog.builder()
                .user(user)
                .errorId(errorId)
                .action(action)
                .created(ZonedDateTime.now())
                .build();
        auditLogRepository.save(auditLog);
    }

}
