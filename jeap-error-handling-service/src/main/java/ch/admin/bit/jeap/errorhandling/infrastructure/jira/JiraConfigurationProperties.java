package ch.admin.bit.jeap.errorhandling.infrastructure.jira;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "jeap.errorhandling.jira")
public class JiraConfigurationProperties implements InitializingBean {

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
     * Password credential for the Jira instance. Alternative to token.
     */

    private String password;

    /**
     * API token for the Jira instance. Alternative to password.
     */
    private String token;

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


    @Override
    public void afterPropertiesSet() {
        if (!StringUtils.hasText(password) && !StringUtils.hasText(token)) {
            throw new IllegalStateException("Either password or token must be set to use Jira.");
        }
    }

}
