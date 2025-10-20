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
     * Username credential for the Jira instance. Alternative to token.
     */
    private String username;

    /**
     * Password credential for the Jira instance. Alternative to token.
     */
    private String password;

    /**
     * API token for the Jira instance. Alternative to username and password.
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
        if (!hasUsernameAndPassword() && !hasToken()) {
            throw new IllegalStateException("Either username/password or token must be set to use Jira.");
        }
    }

    public boolean hasUsernameAndPassword() {
        return StringUtils.hasText(username) && StringUtils.hasText(password);
    }

    public boolean hasToken() {
        return StringUtils.hasText(token);
    }

}
