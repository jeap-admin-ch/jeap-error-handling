package ch.admin.bit.jeap.errorhandling.infrastructure.jira;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
        properties.setToken("token");

        assertThatCode(properties::afterPropertiesSet)
                .doesNotThrowAnyException();
    }

    @Test
    void afterPropertiesSetFailsWhenNeitherUsernamePasswordNorTokenIsSet() {
        JiraConfigurationProperties properties = new JiraConfigurationProperties();
        properties.setBaseUrl("https://jira.example.com");

        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Either username/password or token must be set to use Jira.");
    }

    @ParameterizedTest(name = "username=''{0}'', password=''{1}''")
    @CsvSource({
            "user, ''",
            "'', password",
            "user, '   '",
            "'   ', password"
    })
    void afterPropertiesSetFailsWhenUsernameOrPasswordIsEmptyOrBlank(String username, String password) {
        JiraConfigurationProperties properties = new JiraConfigurationProperties();
        properties.setBaseUrl("https://jira.example.com");
        properties.setUsername(username);
        properties.setPassword(password);

        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Either username/password or token must be set to use Jira.");
    }

    @Test
    void afterPropertiesSetFailsWhenTokenIsBlank() {
        JiraConfigurationProperties properties = new JiraConfigurationProperties();
        properties.setBaseUrl("https://jira.example.com");
        properties.setUsername("user");
        properties.setToken("   ");

        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Either username/password or token must be set to use Jira.");
    }
}
