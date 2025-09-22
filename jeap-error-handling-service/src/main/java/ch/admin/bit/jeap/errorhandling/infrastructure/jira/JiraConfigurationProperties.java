package ch.admin.bit.jeap.errorhandling.infrastructure.jira;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "jeap.errorhandling.jira")
public class JiraConfigurationProperties {

    /**
     * Base URL of the Jira instance.
     */
    @NotBlank
    private String baseUrl;

    /**
     * Username credential for the Jira instance.
     */
    @NotBlank
    private String username;

    /**
     * Password credential for the Jira instance.
     */
    @NotBlank
    private String password;

    /**
     * Timeout for connecting to the Jira instance.
     */
    @Positive
    private int connectTimeoutMs = 5000;

    /**
     * Timeout for reading from the Jira instance.
     */
    @Positive
    private int readTimeoutMs = 20000;

}
