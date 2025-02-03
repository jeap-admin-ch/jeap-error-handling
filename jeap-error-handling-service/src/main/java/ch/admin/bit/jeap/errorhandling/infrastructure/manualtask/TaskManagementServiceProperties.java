package ch.admin.bit.jeap.errorhandling.infrastructure.manualtask;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Configuration
@ConfigurationProperties(prefix = "jeap.errorhandling.task-management.service")
@Data
public class TaskManagementServiceProperties {

    /**
     * If not enabled, no tasks will be send to the manual task service
     */
    private boolean enabled = true;

    /**
     * The URL of the manual task service
     */
    private String url;

    /**
     * The id identifying the oauth2 client in the spring boot oauth2 client registration configuration that should
     * be used to authenticate requests to the task management service.
     */
    private String clientId;

    /**
     * The timeout of connections to the manual task service
     */
    private Duration timeout = Duration.of(5, ChronoUnit.SECONDS);
}
