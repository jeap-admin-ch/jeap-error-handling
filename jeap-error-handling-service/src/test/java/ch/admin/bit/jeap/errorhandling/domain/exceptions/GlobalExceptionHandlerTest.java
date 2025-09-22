package ch.admin.bit.jeap.errorhandling.domain.exceptions;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    @Test
    void testHandleAsBadRequestWithErrorMessage() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        UUID uuid = UUID.randomUUID();
        String errorMessage = String.format("ErrorGroup with UUID %s not found", uuid);
        ErrorGroupNotFoundException exception = new ErrorGroupNotFoundException(uuid);
        ResponseEntity<ErrorResponse> response = handler.handleAsBadRequestWithErrorMessage(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(errorMessage, Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().getStatus());
    }

    @Test
    void testHandleIssueTrackingError() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        String errorMessage = "Issue tracker responded with an error";
        IssueTrackingServerError exception =
                new IssueTrackingServerError(errorMessage, new RuntimeException("cause"));
        ResponseEntity<ErrorResponse> response = handler.handleIssueTrackingError(exception);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals(errorMessage, Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(HttpStatus.BAD_GATEWAY.value(), response.getBody().getStatus());
    }

}
