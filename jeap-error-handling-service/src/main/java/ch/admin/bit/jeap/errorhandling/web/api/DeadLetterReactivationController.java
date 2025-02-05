package ch.admin.bit.jeap.errorhandling.web.api;

import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.KafkaBatchConsumerProducer;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "DeadLetterReactivation")
@RestController
@RequestMapping("/api/deadletter")
@Slf4j
public class DeadLetterReactivationController {

    private final KafkaBatchConsumerProducer kafkaBatchConsumerProducer;

    public DeadLetterReactivationController(KafkaBatchConsumerProducer kafkaBatchConsumerProducer) {
        this.kafkaBatchConsumerProducer = kafkaBatchConsumerProducer;
    }

    @Schema(description = "Reactivates messages from the dead-letter queue and produces them to the target topic")
    @PostMapping("/reactivate")
    @PreAuthorize("hasRole('error','retry')")
    public void reactivateDeadLetters(@RequestParam int maxRecords) {
        log.info("Starting dead-letter reactivation process with maxRecords={}", maxRecords);
        kafkaBatchConsumerProducer.consumeAndProduce(maxRecords);
        log.info("Dead-letter reactivation process completed");
    }
}
