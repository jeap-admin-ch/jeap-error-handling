package ch.admin.bit.jeap.errorhandling.infrastructure.jira;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
public class JiraClient {

    private final JiraConfigurationProperties jiraConfigurationProperties;
    private final JiraRestClient jiraRestClient;

    public JiraClient(JiraConfigurationProperties jiraConfigurationProperties,
                      RestClient.Builder restClientBuilder) {
        this.jiraConfigurationProperties = jiraConfigurationProperties;
        ClientHttpRequestFactorySettings httpSettings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofMillis(jiraConfigurationProperties.getConnectTimeoutMs()))
                .withReadTimeout(Duration.ofMillis(jiraConfigurationProperties.getReadTimeoutMs()));
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect().build(httpSettings);
        String jiraBaseUrl = withoutTrailingSlash(jiraConfigurationProperties.getBaseUrl());
        log.info("Using Jira base URL: {}", jiraBaseUrl);
        RestClient restClient = restClientBuilder.clone()
                .requestFactory(requestFactory)
                .baseUrl(jiraBaseUrl)
                .defaultHeaders(this::setAuth)
                .build();
        HttpServiceProxyFactory proxyFactory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();
        this.jiraRestClient = proxyFactory.createClient(JiraRestClient.class);
    }

    private void setAuth(HttpHeaders headers) {
        if (StringUtils.hasText(jiraConfigurationProperties.getToken())) {
            headers.setBearerAuth(jiraConfigurationProperties.getToken());
        } else {
            headers.setBasicAuth(
                    jiraConfigurationProperties.getUsername(),
                    jiraConfigurationProperties.getPassword(),
                    StandardCharsets.UTF_8);
        }
    }

    public String createIssue(String projectKey, String issueType, String summary, String description) {
        var payload = JiraCreateIssueRequest.from(projectKey, issueType, summary, description);
        try {
            var response = jiraRestClient.createIssue(payload);
            if (response == null || !StringUtils.hasText(response.key())) {
                throw new JiraUnexpectedResponseException(
                        "The response from Jira was empty or the issue key returned was empty.");
            }
            return response.key();
        } catch (RestClientResponseException ex) {
            throw new JiraResponseException(ex.getMessage(), ex.getStatusCode(),ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ex) {
            throw new JiraCommunicationException(ex);
        }
    }

    private static String withoutTrailingSlash(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalArgumentException("Jira base URL must not be blank.");
        }
        return baseUrl.trim().replaceAll("/+$", "");
    }

}
