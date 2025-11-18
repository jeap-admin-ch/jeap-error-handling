package ch.admin.bit.jeap.errorhandling.web.api;

import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.KafkaDeadLetterBatchConsumerProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.context.junit.jupiter.SpringExtension;


import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
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
        when(mockProducer.consumeAndProduceInBatch(anyInt())).thenReturn(10).thenReturn(0);

        DeadLetterReactivationController controller = new DeadLetterReactivationController(mockProducer);

        controller.reactivateDeadLetters(100);

        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(mockProducer, atLeastOnce()).consumeAndProduceInBatch(anyInt()));
    }
}