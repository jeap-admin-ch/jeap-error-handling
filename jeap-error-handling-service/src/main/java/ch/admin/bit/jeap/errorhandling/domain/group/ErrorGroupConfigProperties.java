package ch.admin.bit.jeap.errorhandling.domain.group;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;


@Data
@Configuration
@ConfigurationProperties(prefix = "jeap.errorhandling.error-groups")
public class ErrorGroupConfigProperties {
    /**
     * If errors should be collected into error groups or not.
     */
    private boolean errorGroupingEnabled = true;
}
