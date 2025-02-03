package ch.admin.bit.jeap.errorhandling.infrastructure.manualtask;

/**
 * Exception that may occur during interaction with the task management service.
 */
public class TaskManagementException extends RuntimeException {
    TaskManagementException(String message, Throwable cause) {
        super(message, cause);
    }

    TaskManagementException(String message) {
        super(message);
    }
}
