package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuration of the reactivation of messages from the dead letter topic.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "jeap.errorhandling.dead-letter-reactivation")
public class DeadLetterReactivationProperties {

    /**
     * Maximum number of dead letter messages fetched, forwarded and committed as one batch.
     */
    @Min(1)
    private int maxPollRecords = 500;

    /**
     * A reactivation run ends if it could not make any progress for this duration.
     */
    @NotNull
    private Duration idleTimeout = Duration.ofSeconds(60);
}
