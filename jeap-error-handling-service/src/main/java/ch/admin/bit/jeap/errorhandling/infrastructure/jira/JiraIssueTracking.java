package ch.admin.bit.jeap.errorhandling.infrastructure.jira;

import ch.admin.bit.jeap.errorhandling.domain.exceptions.IssueTrackingBadRequest;
import ch.admin.bit.jeap.errorhandling.domain.exceptions.IssueTrackingCommunicationError;
import ch.admin.bit.jeap.errorhandling.domain.exceptions.IssueTrackingServerError;
import ch.admin.bit.jeap.errorhandling.domain.issue.IssueTracking;
import lombok.RequiredArgsConstructor;
import org.springframework.web.client.RestClientResponseException;

@RequiredArgsConstructor
public class JiraIssueTracking implements IssueTracking {

    private final JiraConfigurationProperties jiraConfigurationProperties;
    private final JiraClient jiraClient;

    @Override
    public String createIssue(String type, String project, String summary, String description) {
        try {
            return jiraClient.createIssue(project, type, summary, description, jiraConfigurationProperties.getUsername());
        } catch (RestClientResponseException rcre) {
            if (rcre.getStatusCode().is4xxClientError()) {
                throw new IssueTrackingBadRequest("Bad 'create issue request' reported by Jira.", rcre);
            } else if (rcre.getStatusCode().is5xxServerError()) {
                throw new IssueTrackingServerError("Server error from Jira.", rcre);
            } else throw rcre;
        } catch (JiraUnexpectedResponseException jure) {
            throw new IssueTrackingServerError("Unexpected response from Jira.", jure);
        } catch (JiraCommunicationException jce) {
            throw new IssueTrackingCommunicationError("Communication error with Jira.", jce);
        }
    }

}
