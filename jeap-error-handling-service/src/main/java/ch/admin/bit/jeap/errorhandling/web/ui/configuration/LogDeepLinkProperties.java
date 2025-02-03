package ch.admin.bit.jeap.errorhandling.web.ui.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "log.deep-link")
public class LogDeepLinkProperties {

    /**
     * The Base URL of the log-system. Must contain a {traceId} token in order to be replaced
     * with the traceId of the error event.
     */
    private String baseUrl;

}
