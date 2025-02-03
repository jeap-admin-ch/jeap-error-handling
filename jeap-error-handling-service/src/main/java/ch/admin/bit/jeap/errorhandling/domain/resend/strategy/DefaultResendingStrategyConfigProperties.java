package ch.admin.bit.jeap.errorhandling.domain.resend.strategy;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration properties for the @{@link DefaultResendingStrategy}
 */
@Configuration
@ConfigurationProperties(prefix = "jeap.errorhandling.resend.default-resending-strategy")
@Data
class DefaultResendingStrategyConfigProperties {
    /**
     * The initial delay to resend events
     */
    private Duration delay = Duration.ofSeconds(30);
    /**
     * The maximal number of retries
     */
    private int maxRetries = 15;
    /**
     * Should the time to resend increase exponentially?
     */
    private boolean exponentialBackoffEnabled = true;
    /**
     * The exponential growth factor
     */
    private int exponentialBackoffFactor = 2;
    /**
     * The maximal delay to resent an event
     */
    private Duration exponentialBackoffMaxDelay = Duration.ofDays(1);
}
