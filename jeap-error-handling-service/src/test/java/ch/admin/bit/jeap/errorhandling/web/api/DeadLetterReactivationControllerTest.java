package ch.admin.bit.jeap.errorhandling.web.api;

import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.KafkaDeadLetterBatchConsumerProducer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DeadLetterReactivationControllerTest {

    @Test
    void reactivateDeadLetters_shouldThrowExceptionForInvalidMaxRecords() {
        KafkaDeadLetterBatchConsumerProducer mockProducer = mock(KafkaDeadLetterBatchConsumerProducer.class);
        DeadLetterReactivationController controller = new DeadLetterReactivationController(mockProducer);

        // Test too small
        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class,
                () -> controller.reactivateDeadLetters(0));
        assertEquals(HttpStatus.BAD_REQUEST, ex1.getStatusCode());
        assertEquals("maxRecords must be between 1 and 100000", ex1.getReason());

        // Test too large
        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class,
                () -> controller.reactivateDeadLetters(100001));
        assertEquals(HttpStatus.BAD_REQUEST, ex2.getStatusCode());
        assertEquals("maxRecords must be between 1 and 100000", ex2.getReason());
    }

    @Test
    void reactivateDeadLetters_shouldProcessMessagesAsync() throws NoSuchMethodException {
        KafkaDeadLetterBatchConsumerProducer mockProducer = mock(KafkaDeadLetterBatchConsumerProducer.class);

        DeadLetterReactivationController controller = new DeadLetterReactivationController(mockProducer);

        controller.reactivateDeadLetters(100);

        verify(mockProducer).consumeAndProduce(100);
        ResponseStatus responseStatus = DeadLetterReactivationController.class
                .getMethod("reactivateDeadLetters", int.class).getAnnotation(ResponseStatus.class);
        assertEquals(HttpStatus.ACCEPTED, responseStatus.value());
    }
}