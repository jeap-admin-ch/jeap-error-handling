package ch.admin.bit.jeap.errorhandling.infrastructure.jira;

public class JiraCommunicationException extends RuntimeException {

    public JiraCommunicationException(Throwable cause) {
        super("Communication with Jira failed because of: " + cause.getMessage(), cause);
    }

}

