package ch.admin.bit.jeap.errorhandling.infrastructure.jira;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@AutoConfiguration
@ConditionalOnProperty("jeap.errorhandling.jira.base-url")
@EnableConfigurationProperties(JiraConfigurationProperties.class)
public class JiraIssueTrackingConfiguration {

    @Bean
    public JiraIssueTracking jiraIssueTracking(JiraConfigurationProperties jiraConfigurationProperties, JiraClient jiraClient) {
        return new JiraIssueTracking(jiraConfigurationProperties, jiraClient);
    }

    @Bean
    JiraClient jiraClient(JiraConfigurationProperties jiraConfigurationProperties, RestClient.Builder restClientBuilder) {
        return new JiraClient(jiraConfigurationProperties, restClientBuilder);
    }

}
