package ch.admin.bit.jeap.errorhandling.domain.group;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public class ErrorGroupIssueTrackingProperties {

    /**
     * The issue tracking project to create the issue in.
     */
    @NotBlank
    private String project;

    /**
     * The issue type to use for the created issue.
     */
    @NotBlank
    private String issueType = "Bug";

    /**
     * The template for the summary of the created issue. Use one or more of the following placeholders:
     * {group-id}, {group-created-datetime}, {source}, {message-type}, {error-code}, {error-count}
     */
    @NotBlank
    private String issueSummaryTemplate = "Processing of '{message-type}' from '{source}' fails with '{error-code}'";

    /**
     * The template for the URL of an error handling service group. Use {group-id} as the placeholder for the group id.
     */
    @NotBlank
    private String errorHandlingServiceGroupUrlTemplate;

}
