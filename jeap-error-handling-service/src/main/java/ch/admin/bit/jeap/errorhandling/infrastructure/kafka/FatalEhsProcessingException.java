package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

public class FatalEhsProcessingException extends RuntimeException {
    public FatalEhsProcessingException(Throwable cause) {
        super("Classified 'fatal': " + cause.getMessage(), cause);
    }
}
