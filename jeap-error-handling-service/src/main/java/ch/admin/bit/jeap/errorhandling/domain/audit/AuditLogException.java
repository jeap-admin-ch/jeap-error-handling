package ch.admin.bit.jeap.errorhandling.domain.audit;

public class AuditLogException extends RuntimeException {

    private AuditLogException(String message) {
        super(message);
    }

    static AuditLogException noAuthenticatedUserException() {
        return new AuditLogException("The audit log requires an authenticated user.");
    }

}
