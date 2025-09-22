package ch.admin.bit.jeap.errorhandling.domain.issue;

import ch.admin.bit.jeap.errorhandling.domain.exceptions.IssueTrackingBadRequest;
import ch.admin.bit.jeap.errorhandling.domain.exceptions.IssueTrackingCommunicationError;
import ch.admin.bit.jeap.errorhandling.domain.exceptions.IssueTrackingServerError;

public interface IssueTracking {

    /**
     * Create a new issue in the issue tracking system.
     * @param type The issue's type.
     * @param project The issue's project.
     * @param summary The issue's summary.
     * @param description The issue's description.
     * @return The issue's identifier.
     * @throws IssueTrackingBadRequest if the issue tracking system declared a request to be invalid.
     * @throws IssueTrackingServerError if the issue tracking system could not process the request successfully.
     * @throws IssueTrackingCommunicationError if the communication with the issue tracking system failed.
     */
    String createIssue(String type, String project, String summary, String description);

}
