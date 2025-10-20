package ch.admin.bit.jeap.errorhandling.infrastructure.jira;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.NonNull;

record JiraCreateIssueRequest(JiraIssueFieldsSpec fields) {

    static JiraCreateIssueRequest from(String projectKey, String issueType, String summary, String description) {
        return new JiraCreateIssueRequest(new JiraIssueFieldsSpec(
                new JiraProjectSpec(projectKey),
                new JiraIssueTypeSpec(issueType),
                summary,
                description)
        );
    }

    record JiraIssueFieldsSpec(@NonNull JiraProjectSpec project,
                               @NonNull @JsonProperty("issuetype") JiraIssueTypeSpec issueType,
                               @NonNull String summary,
                               @NonNull String description) {
    }
    record JiraProjectSpec(@NonNull String key) {}
    record JiraIssueTypeSpec(@NonNull String name) {}
}
