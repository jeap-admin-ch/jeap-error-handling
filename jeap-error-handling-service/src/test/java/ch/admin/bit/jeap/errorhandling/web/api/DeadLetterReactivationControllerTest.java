package ch.admin.bit.jeap.errorhandling.web.api;

import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.KafkaDeadLetterBatchConsumerProducer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
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

        // Test too large
        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class,
                () -> controller.reactivateDeadLetters(200000));
        assertEquals(HttpStatus.BAD_REQUEST, ex2.getStatusCode());
    }

    @Test
    void reactivateDeadLetters_shouldProcessMessagesAsync() {
        KafkaDeadLetterBatchConsumerProducer mockProducer = mock(KafkaDeadLetterBatchConsumerProducer.class);

        DeadLetterReactivationController controller = new DeadLetterReactivationController(mockProducer);

        controller.reactivateDeadLetters(100);

        verify(mockProducer).consumeAndProduce(100);
    }
}