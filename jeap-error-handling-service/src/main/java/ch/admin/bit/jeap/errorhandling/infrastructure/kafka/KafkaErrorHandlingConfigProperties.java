package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Data
@Validated
@ConfigurationProperties(prefix = "jeap.errorhandling.kafka.errorhandling")
public class KafkaErrorHandlingConfigProperties {

    /**
     * The interval between two retries when retrying to process a failed message that caused a recoverable error
     * in the EHS while processing the message.
     */
    @NotNull
    private Duration retryInterval = Duration.ofSeconds(30);

}
