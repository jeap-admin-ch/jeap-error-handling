package ch.admin.bit.jeap.errorhandling.infrastructure.jira;

public class JiraUnexpectedResponseException extends RuntimeException {

    JiraUnexpectedResponseException(String message) {
        super("Unexpected response from Jira: " + message + ".");
    }

}

