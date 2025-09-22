package ch.admin.bit.jeap.errorhandling.infrastructure.jira;

import ch.admin.bit.jeap.errorhandling.domain.exceptions.IssueTrackingBadRequest;
import ch.admin.bit.jeap.errorhandling.domain.exceptions.IssueTrackingCommunicationError;
import ch.admin.bit.jeap.errorhandling.domain.exceptions.IssueTrackingServerError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JiraIssueTrackingTest {

    private static final String PROJECT = "PROJ";
    private static final String ISSUE_TYPE = "Bug";
    private static final String SUMMARY = "the summary";
    private static final String DESCRIPTION = "the description";
    private static final String REPORTER = "jira-user";

    @Mock
    private JiraConfigurationProperties jiraConfigurationProperties;
    @Mock
    private JiraClient jiraClient;

    private JiraIssueTracking jiraIssueTracking;

    @BeforeEach
    void setUp() {
        when(jiraConfigurationProperties.getUsername()).thenReturn(REPORTER);
        jiraIssueTracking = new JiraIssueTracking(jiraConfigurationProperties, jiraClient);
    }

    @Test
    void createIssueReturnsIssueKeyWhenClientSucceeds() {
        when(jiraClient.createIssue(PROJECT, ISSUE_TYPE, SUMMARY, DESCRIPTION, REPORTER)).thenReturn("PROJ-123");

        String issueKey = jiraIssueTracking.createIssue(ISSUE_TYPE, PROJECT, SUMMARY, DESCRIPTION);

        assertThat(issueKey).isEqualTo("PROJ-123");
        verify(jiraClient).createIssue(PROJECT, ISSUE_TYPE, SUMMARY, DESCRIPTION, REPORTER);
    }

    @Test
    void createIssueTransforms4xxResponsesIntoBadRequest() {
        RestClientResponseException exception = new RestClientResponseException(
                "Bad request",
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                null,
                null,
                null);
        doThrow(exception).when(jiraClient).createIssue(PROJECT, ISSUE_TYPE, SUMMARY, DESCRIPTION, REPORTER);

        assertThatThrownBy(() -> jiraIssueTracking.createIssue(ISSUE_TYPE, PROJECT, SUMMARY, DESCRIPTION))
                .isInstanceOf(IssueTrackingBadRequest.class)
                .hasCause(exception)
                .hasMessage("Bad 'create issue request' reported by Jira.");
    }

    @Test
    void createIssueTransforms5xxResponsesIntoServerError() {
        RestClientResponseException exception = new RestClientResponseException(
                "Service unavailable",
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                null,
                null,
                null);
        doThrow(exception).when(jiraClient).createIssue(PROJECT, ISSUE_TYPE, SUMMARY, DESCRIPTION, REPORTER);

        assertThatThrownBy(() -> jiraIssueTracking.createIssue(ISSUE_TYPE, PROJECT, SUMMARY, DESCRIPTION))
                .isInstanceOf(IssueTrackingServerError.class)
                .hasCause(exception)
                .hasMessage("Server error from Jira.");
    }

    @Test
    void createIssuePropagatesNon4xxOr5xxResponses() {
        RestClientResponseException exception = new RestClientResponseException(
                "Redirect",
                HttpStatus.MOVED_PERMANENTLY.value(),
                HttpStatus.MOVED_PERMANENTLY.getReasonPhrase(),
                null,
                null,
                null);
        doThrow(exception).when(jiraClient).createIssue(PROJECT, ISSUE_TYPE, SUMMARY, DESCRIPTION, REPORTER);

        assertThatThrownBy(() -> jiraIssueTracking.createIssue(ISSUE_TYPE, PROJECT, SUMMARY, DESCRIPTION))
                .isSameAs(exception);
    }

    @Test
    void createIssueTransformsUnexpectedResponseIntoServerError() {
        JiraUnexpectedResponseException exception = new JiraUnexpectedResponseException("missing key");
        doThrow(exception).when(jiraClient).createIssue(PROJECT, ISSUE_TYPE, SUMMARY, DESCRIPTION, REPORTER);

        assertThatThrownBy(() -> jiraIssueTracking.createIssue(ISSUE_TYPE, PROJECT, SUMMARY, DESCRIPTION))
                .isInstanceOf(IssueTrackingServerError.class)
                .hasCause(exception)
                .hasMessage("Unexpected response from Jira.");
    }

    @Test
    void createIssueTransformsCommunicationFailuresIntoCommunicationError() {
        JiraCommunicationException exception = new JiraCommunicationException(new RuntimeException("connection reset"));
        doThrow(exception).when(jiraClient).createIssue(PROJECT, ISSUE_TYPE, SUMMARY, DESCRIPTION, REPORTER);

        assertThatThrownBy(() -> jiraIssueTracking.createIssue(ISSUE_TYPE, PROJECT, SUMMARY, DESCRIPTION))
                .isInstanceOf(IssueTrackingCommunicationError.class)
                .hasCause(exception)
                .hasMessage("Communication error with Jira.");
    }
}
