package ch.admin.bit.jeap.errorhandling.domain.exceptions;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    @Test
    void handleBadRequestException() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        String errorMessage = "Ticket number already exists";
        TicketNumberAlreadyExistsException exception = new TicketNumberAlreadyExistsException(errorMessage);
        ResponseEntity<ErrorResponse> response = handler.handleBadRequestException(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(errorMessage, Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().getStatus());
    }

    @Test
    void handleBadRequestException_WithDifferentMessage() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        UUID uuid = UUID.randomUUID();
        String errorMessage = String.format("ErrorGroup with UUID %s not found", uuid);
        ErrorGroupNotFoundException exception = new ErrorGroupNotFoundException(uuid);
        ResponseEntity<ErrorResponse> response = handler.handleBadRequestException(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(errorMessage, Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().getStatus());
    }

    @Test
    void handleBadRequestException_WithNullMessage() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        String errorMessage = "ErrorGroup with UUID null not found";
        ErrorGroupNotFoundException exception = new ErrorGroupNotFoundException(null);
        ResponseEntity<ErrorResponse> response = handler.handleBadRequestException(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(errorMessage, Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().getStatus());
    }
}