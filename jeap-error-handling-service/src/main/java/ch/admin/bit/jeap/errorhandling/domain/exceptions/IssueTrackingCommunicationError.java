package ch.admin.bit.jeap.errorhandling.domain.exceptions;

/**
 * This exception is thrown when an error occurs while communicating with the issue tracking system.
 */
public class IssueTrackingCommunicationError extends RuntimeException {
    public IssueTrackingCommunicationError(String message, Throwable cause) {
        super(message, cause);
    }
}
