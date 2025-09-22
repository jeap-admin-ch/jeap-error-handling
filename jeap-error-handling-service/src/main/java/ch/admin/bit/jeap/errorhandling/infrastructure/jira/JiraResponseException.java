package ch.admin.bit.jeap.errorhandling.infrastructure.jira;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

public class JiraResponseException extends RuntimeException {

    @Getter
    private final HttpStatusCode statusCode;
    @Getter
    private final String response;

    public JiraResponseException(String message, HttpStatusCode statusCode, String response, Throwable cause) {
        super("Jira response indicates an error: " + message, cause);
        this.statusCode = statusCode;
        this.response = response;
    }

}

