package ch.admin.bit.jeap.errorhandling.web.ui.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "jeap.errorhandling.dead-letter-count")
public class DeadLetterCountConfigProperties {

    /**
     * Interval at which the dead letter pending message count is re-determined, in milliseconds. The UI
     * polls at the same interval, see {@link ConfigurationController#getDeadLetterCountUpdateFrequencyMillis()}.
     */
    private long updateFrequencyMillis = 30000;
}
