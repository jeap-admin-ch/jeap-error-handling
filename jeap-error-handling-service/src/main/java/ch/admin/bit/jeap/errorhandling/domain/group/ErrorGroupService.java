package ch.admin.bit.jeap.errorhandling.domain.group;

import ch.admin.bit.jeap.errorhandling.domain.exceptions.ErrorGroupAlreadyHasATicketNumberAssignedException;
import ch.admin.bit.jeap.errorhandling.domain.exceptions.ErrorGroupNotFoundException;
import ch.admin.bit.jeap.errorhandling.domain.issue.IssueTracking;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorGroup;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorGroupRepository;
import ch.admin.bit.jeap.errorhandling.web.api.ErrorGroupSearchCriteria;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.util.StringUtils.hasText;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ErrorGroupService {

    public static final String A_LONG_LONG_TIME_AGO = "1971-03-11T00:00:00Z";
    public static final String FAR_FAR_IN_THE_FUTURE = "2999-12-31T00:00:00Z";

    private final ErrorGroupConfigProperties errorGroupConfigProperties;
    private final ErrorGroupRepository errorGroupRepository;
    private final PlatformTransactionManager transactionManager;
    private final IssueDescriptionGenerator issueDescriptionGenerator;
    private final IssueSummaryGenerator issueSummaryGenerator;
    private final Optional<IssueTracking> issueTracking; // may be not configured

    public ErrorGroup assignToErrorGroup(Error error) {
        // error grouping is based on the error's stack trace hash: no hash -> no group
        if (errorGroupConfigProperties.isErrorGroupingEnabled() && hasText(error.getErrorEventData().getStackTraceHash())) {
            ErrorGroup errorGroup = findOrCreateMatchingErrorGroup(error);
            error.setErrorGroup(errorGroup);
            return errorGroup;
        } else {
            return null;
        }
    }

    public ErrorGroup updateTicketNumber(UUID errorGroupId, String ticketNumber) {
        ErrorGroup errorGroup = findErrorGroup(errorGroupId);
        errorGroup.setTicketNumber(StringUtils.isNotBlank(ticketNumber) ? ticketNumber : null);
        return errorGroup;
    }

    public ErrorGroup updateFreeText(UUID errorGroupId, String freeText) {
        ErrorGroup errorGroup = findErrorGroup(errorGroupId);
        errorGroup.setFreeText(freeText);
        return errorGroup;
    }

    public ErrorGroupAggregatedData getErrorGroupAggregatedData(UUID errorGroupId) {
        return errorGroupRepository.findErrorGroupAggregatedData(errorGroupId).
                orElseThrow(() -> new ErrorGroupNotFoundException(errorGroupId));
    }

    /**
     * Finds aggregated error group data based on the provided search criteria.
     *
     * @param criteria the search criteria for error groups
     * @return a list containing aggregated data of error groups matching the criteria
     */
    public ErrorGroupAggregatedDataList findErrorGroupAggregatedData(ErrorGroupSearchCriteria criteria) {
        Boolean noTicket = criteria.getNoTicket().orElse(false);

        // Use extreme date values if not provided to avoid null checks in the query
        ZonedDateTime dateFrom = criteria.getDateFrom().orElse(ZonedDateTime.parse(A_LONG_LONG_TIME_AGO));
        ZonedDateTime dateTo = criteria.getDateTo().orElse(ZonedDateTime.parse(FAR_FAR_IN_THE_FUTURE));

        String source = criteria.getSource().orElse(null);
        String messageType = criteria.getMessageType().orElse(null);
        String errorCode = criteria.getErrorCode().orElse(null);
        String jiraTicket = criteria.getJiraTicket().orElse(null);

        Page<ErrorGroupAggregatedData> groupAggregatedData = errorGroupRepository.findErrorGroupAggregatedData(
                noTicket,
                dateFrom,
                dateTo,
                source,
                messageType,
                errorCode,
                jiraTicket,
                criteria.getPageable());
        return new ErrorGroupAggregatedDataList(groupAggregatedData.getTotalElements(), groupAggregatedData.getContent());
    }

    public String createIssue(UUID errorGroupId) {
        if (issueTracking.isEmpty()) {
            throw new IllegalStateException("Issue tracking is not configured.");
        }
        Optional<ErrorGroupAggregatedData> egDataOptional = errorGroupRepository.findErrorGroupAggregatedData(errorGroupId);
        if (egDataOptional.isEmpty()) {
            throw new ErrorGroupNotFoundException(errorGroupId);
        }
        ErrorGroupAggregatedData egData = egDataOptional.get();
        if (StringUtils.isNotBlank(egData.getTicketNumber())) {
            throw new ErrorGroupAlreadyHasATicketNumberAssignedException(errorGroupId, egData.getTicketNumber());
        }
        ErrorGroupIssueTrackingProperties issueTrackingProperties = errorGroupConfigProperties.getIssueTracking();
        String description = issueDescriptionGenerator.generateDescription(egData);
        String summary = issueSummaryGenerator.generateIssueSummary(egData);
        String issueId = issueTracking.get().createIssue(
                issueTrackingProperties.getIssueType(),
                issueTrackingProperties.getProject(),
                summary, description);
        updateTicketNumber(errorGroupId, issueId);
        return issueId;
    }

    private ErrorGroup findOrCreateMatchingErrorGroup(Error error) {
        ErrorGroup errorGroup = ErrorGroup.from(error);
        try {
            return saveOrGetErrorGroup(errorGroup);
        } catch (TransactionException e) {
            if (e.contains(DataIntegrityViolationException.class)) {
                // Error group already created by a concurrent transaction
                // -> retry to get the now existing error group
                return saveOrGetErrorGroup(errorGroup);
            }
            throw e;
        }
    }

    private ErrorGroup saveOrGetErrorGroup(ErrorGroup errorGroup) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> {
            Optional<ErrorGroup> existingErrorGroup = findMatchingErrorGroup(errorGroup);
            if (existingErrorGroup.isEmpty()) {
                log.debug("Creating new error group: {}.", errorGroup);
                return errorGroupRepository.save(errorGroup);
            }
            else {
                log.debug("Found existing error group: {}.", existingErrorGroup.get());
                return existingErrorGroup.get();
            }
        });
    }

    private ErrorGroup findErrorGroup(UUID errorGroupId) {
        return errorGroupRepository.findById(errorGroupId).
                orElseThrow(() -> new ErrorGroupNotFoundException(errorGroupId));
    }

    private Optional<ErrorGroup> findMatchingErrorGroup(ErrorGroup errorGroup) {
        return errorGroupRepository.findByErrorPublisherAndErrorCodeAndEventNameAndErrorStackTraceHash(
                errorGroup.getErrorPublisher(), errorGroup.getErrorCode(), errorGroup.getEventName(),
                errorGroup.getErrorStackTraceHash());
    }

}
