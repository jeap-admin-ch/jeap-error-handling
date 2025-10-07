package ch.admin.bit.jeap.errorhandling.infrastructure.jira;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JiraConfigurationPropertiesTest {

    @Test
    void afterPropertiesSetSucceedsWhenPasswordIsSet() {
        JiraConfigurationProperties properties = new JiraConfigurationProperties();
        properties.setBaseUrl("https://jira.example.com");
        properties.setUsername("user");
        properties.setPassword("password");

        assertThatCode(properties::afterPropertiesSet)
                .doesNotThrowAnyException();
    }

    @Test
    void afterPropertiesSetSucceedsWhenTokenIsSet() {
        JiraConfigurationProperties properties = new JiraConfigurationProperties();
        properties.setBaseUrl("https://jira.example.com");
        properties.setUsername("user");
        properties.setToken("token");

        assertThatCode(properties::afterPropertiesSet)
                .doesNotThrowAnyException();
    }

    @Test
    void afterPropertiesSetFailsWhenNeitherPasswordNorTokenIsSet() {
        JiraConfigurationProperties properties = new JiraConfigurationProperties();
        properties.setBaseUrl("https://jira.example.com");
        properties.setUsername("user");

        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Either password or token must be set to use Jira.");
    }

    @Test
    void afterPropertiesSetFailsWhenPasswordIsEmpty() {
        JiraConfigurationProperties properties = new JiraConfigurationProperties();
        properties.setBaseUrl("https://jira.example.com");
        properties.setUsername("user");
        properties.setPassword("");

        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Either password or token must be set to use Jira.");
    }

    @Test
    void afterPropertiesSetFailsWhenTokenIsEmpty() {
        JiraConfigurationProperties properties = new JiraConfigurationProperties();
        properties.setBaseUrl("https://jira.example.com");
        properties.setUsername("user");
        properties.setToken("");

        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Either password or token must be set to use Jira.");
    }

    @Test
    void afterPropertiesSetFailsWhenPasswordIsBlank() {
        JiraConfigurationProperties properties = new JiraConfigurationProperties();
        properties.setBaseUrl("https://jira.example.com");
        properties.setUsername("user");
        properties.setPassword("   ");

        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Either password or token must be set to use Jira.");
    }

    @Test
    void afterPropertiesSetFailsWhenTokenIsBlank() {
        JiraConfigurationProperties properties = new JiraConfigurationProperties();
        properties.setBaseUrl("https://jira.example.com");
        properties.setUsername("user");
        properties.setToken("   ");

        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Either password or token must be set to use Jira.");
    }
}
