package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

public class RecoverableEhsProcessingException extends RuntimeException {
    public RecoverableEhsProcessingException(Throwable cause) {
        super("Classified 'recoverable': " + cause.getMessage(), cause);
    }
}
