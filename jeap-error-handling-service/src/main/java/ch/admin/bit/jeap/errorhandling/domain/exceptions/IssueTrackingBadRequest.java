package ch.admin.bit.jeap.errorhandling.domain.exceptions;

/**
 * This exception is thrown when the issue tracking system rejects the request.
 */
public class IssueTrackingBadRequest extends RuntimeException {
    public IssueTrackingBadRequest(String message, Throwable cause) {
        super(message, cause);
    }
}
