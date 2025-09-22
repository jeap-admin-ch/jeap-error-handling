package ch.admin.bit.jeap.errorhandling.domain.exceptions;

/**
 * This exception is thrown when the issue tracking system responds with an error.
 */
public class IssueTrackingServerError extends RuntimeException {
    public IssueTrackingServerError(String message, Throwable cause) {
        super(message, cause);
    }
}
