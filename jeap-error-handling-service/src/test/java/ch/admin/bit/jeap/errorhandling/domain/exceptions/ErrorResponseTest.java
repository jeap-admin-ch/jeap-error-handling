package ch.admin.bit.jeap.errorhandling.domain.exceptions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorResponseTest {
    @Test
    void testErrorResponseConstructorAndGetters() {
        String message = "Error occurred";
        int status = 400;
        ErrorResponse errorResponse = new ErrorResponse(message, status);
        assertEquals(message, errorResponse.getMessage());
        assertEquals(status, errorResponse.getStatus());
    }

    @Test
    void testSetMessage() {
        ErrorResponse response = new ErrorResponse("Initial message", 400);
        response.setMessage("updated message");
        assertEquals("updated message", response.getMessage());
    }

    @Test
    void testSetStatus() {
        ErrorResponse response = new ErrorResponse("Initial status", 400);
        response.setStatus(200);
        assertEquals(200, response.getStatus());
    }

}