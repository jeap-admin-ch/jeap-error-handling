package ch.admin.bit.jeap.errorhandling.web.api;

import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.KafkaDeadLetterBatchConsumerProducer;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.CompletableFuture;

@Tag(name = "DeadLetterReactivation")
@RestController
@RequestMapping("/api/deadletter")
@Slf4j
public class DeadLetterReactivationController {

    private final KafkaDeadLetterBatchConsumerProducer kafkaDeadLetterBatchConsumerProducer;

    public DeadLetterReactivationController(KafkaDeadLetterBatchConsumerProducer kafkaDeadLetterBatchConsumerProducer) {
        this.kafkaDeadLetterBatchConsumerProducer = kafkaDeadLetterBatchConsumerProducer;
    }

    @Schema(description = "Reactivates messages from the dead-letter queue and produces them to the target topic")
    @PostMapping("/reactivate")
    @PreAuthorize("hasRole('error','retry')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void reactivateDeadLetters(@RequestParam int maxRecords) {
        if (maxRecords < 1 || maxRecords > 100000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "maxRecords must be between 1 and 500");
        }
        log.info("Starting dead-letter reactivation process with maxRecords={}", maxRecords);

        CompletableFuture.runAsync(() -> {
            int remaining = maxRecords;
            while (remaining > 0) {
                int batchSize = Math.min(remaining, 50);
                int processed = kafkaDeadLetterBatchConsumerProducer.consumeAndProduceInBatch(batchSize);
                if (processed == 0) break;
                remaining -= processed;
            }
            log.info("Dead-letter reactivation process completed");
        });
    }
}
