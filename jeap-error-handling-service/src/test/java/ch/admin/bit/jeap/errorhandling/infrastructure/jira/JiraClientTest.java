package ch.admin.bit.jeap.errorhandling.infrastructure.jira;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
class JiraClientTest {

    private static final String ISSUE_API_CONTEXT_PATH = "/rest/api/2/issue";
    private static final String USERNAME = "jira-user";
    private static final String PASSWORD = "secure-password";

    @Test
    void createIssueReturnsIssueKeyWhenSuccessful(WireMockRuntimeInfo runtimeInfo) {
        JiraClient jiraClient = createJiraClient(runtimeInfo.getHttpBaseUrl());

        stubFor(post(urlEqualTo(ISSUE_API_CONTEXT_PATH))
                .withBasicAuth(USERNAME, PASSWORD)
                .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(MediaType.APPLICATION_JSON_VALUE))
                .withRequestBody(equalToJson("""
                        {
                          "fields": {
                            "project": {"key": "PROJ"},
                            "issuetype": {"name": "Bug"},
                            "summary": "the summary",
                            "description": "the description",
                            "reporter": {"name": "reporter-user"}
                          }
                        }
                        """, true, true))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.CREATED.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "id": "10000",
                                  "key": "PROJ-123",
                                  "self": "https://jira.example.com/browse/PROJ-123"
                                }
                                """)));

        String issueKey = jiraClient.createIssue("PROJ", "Bug", "the summary", "the description", "reporter-user");

        assertThat(issueKey).isEqualTo("PROJ-123");
    }

    @Test
    void createIssueThrowsResponseExceptionForUnauthorized(WireMockRuntimeInfo runtimeInfo) {
        JiraClient jiraClient = createJiraClient(runtimeInfo.getHttpBaseUrl());

        stubFor(post(urlEqualTo(ISSUE_API_CONTEXT_PATH))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.UNAUTHORIZED.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "errorMessages": ["Invalid credentials"],
                                  "errors": {}
                                }
                                """)));

        assertThatThrownBy(
                () -> jiraClient.createIssue("PROJ", "Bug", "summary", "description", "reporter"))
                .isInstanceOfSatisfying(JiraResponseException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
                    assertThat(ex.getResponse()).contains("Invalid credentials");
                })
                .hasMessageContaining("Jira response indicates an error");
    }

    @Test
    void createIssueThrowsResponseExceptionForBadRequest(WireMockRuntimeInfo runtimeInfo) {
        JiraClient jiraClient = createJiraClient(runtimeInfo.getHttpBaseUrl());

        stubFor(post(urlEqualTo(ISSUE_API_CONTEXT_PATH))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.BAD_REQUEST.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "errorMessages": ["Project does not exist"],
                                  "errors": {"project": "Project PROJ is unknown"}
                                }
                                """)));

        assertThatThrownBy(() -> jiraClient.createIssue("PROJ", "Bug", "summary", "description", "reporter"))
                .isInstanceOfSatisfying(JiraResponseException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());
                    assertThat(ex.getResponse()).contains("Project does not exist");
                    assertThat(ex.getResponse()).contains("\"project\": \"Project PROJ is unknown\"");
                })
                .hasMessageContaining("Jira response indicates an error");
    }

    @Test
    void createIssueThrowsResponseExceptionForServerError(WireMockRuntimeInfo runtimeInfo) {
        JiraClient jiraClient = createJiraClient(runtimeInfo.getHttpBaseUrl());

        stubFor(post(urlEqualTo(ISSUE_API_CONTEXT_PATH))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.SERVICE_UNAVAILABLE.value())
                        .withBody("Service unavailable")));

        assertThatThrownBy(() -> jiraClient.createIssue("PROJ", "Bug", "summary", "description", "reporter"))
                .isInstanceOfSatisfying(JiraResponseException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
                    assertThat(ex.getResponse()).contains("Service unavailable");
                })
                .hasMessageContaining("Jira response indicates an error");
    }

    @Test
    void createIssueThrowsCommunicationExceptionWhenJiraIsNotReachable() {
        JiraClient jiraClient = createJiraClient("http://localhost:65530");

        assertThatThrownBy(() -> jiraClient.createIssue("PROJ", "Bug", "summary", "description", "reporter"))
                .isInstanceOf(JiraCommunicationException.class);
    }

    @Test
    void createIssueThrowsUnexpectedResponseExceptionWhenKeyMissing(WireMockRuntimeInfo runtimeInfo) {
        JiraClient jiraClient = createJiraClient(runtimeInfo.getHttpBaseUrl());

        stubFor(post(urlEqualTo(ISSUE_API_CONTEXT_PATH))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.CREATED.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "id": "10000"
                                }
                                """)));

        assertThatThrownBy(() -> jiraClient.createIssue("PROJ", "Bug", "summary", "description", "reporter"))
                .isInstanceOf(JiraUnexpectedResponseException.class)
                .hasMessageContaining("Unexpected response from Jira");
    }

    private JiraClient createJiraClient(String baseUrl) {
        JiraConfigurationProperties properties = new JiraConfigurationProperties();
        properties.setBaseUrl(baseUrl);
        properties.setUsername(USERNAME);
        properties.setPassword(PASSWORD);

        return new JiraClient(properties, RestClient.builder());
    }

}
