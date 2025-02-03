package ch.admin.bit.jeap.errorhandling.infrastructure.manualtask;

/**
 * Exception to signal that a requested task could not be found.
 */
public class TaskNotFoundException extends TaskManagementException {
    TaskNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    TaskNotFoundException(String message) {
        super(message);
    }
}
