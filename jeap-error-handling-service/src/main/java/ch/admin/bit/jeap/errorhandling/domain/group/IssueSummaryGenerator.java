package ch.admin.bit.jeap.errorhandling.domain.group;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.PropertyPlaceholderHelper;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class IssueSummaryGenerator {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);

    private final ErrorGroupConfigProperties ehgConfigProperties;
    private final PropertyPlaceholderHelper placeholderHelper = new PropertyPlaceholderHelper(
            "{", "}", null, null, false);

    public enum IssueSummaryParameters {
        GROUP_ID("group-id"),
        GROUP_CREATED_DATETIME("group-created-datetime"),
        SOURCE("source"),
        MESSAGE_TYPE("message-type"),
        ERROR_CODE("error-code"),
        ERROR_COUNT("error-count");

        private final String parameterName;

        IssueSummaryParameters(String parameterName) {
            this.parameterName = parameterName;
        }

        public String parameterName() {
            return parameterName;
        }
    }

    public String generateIssueSummary(ErrorGroupAggregatedData egData) {
        return generateIssueSummary(
                egData.getGroupId().toString(),
                egData.getGroupCreatedAt(),
                egData.getErrorPublisher(),
                egData.getErrorEvent(),
                egData.getErrorCode(),
                egData.getErrorCount()
                );
    }

    String generateIssueSummary(String groupId,
                                       ZonedDateTime groupCreatedDatetime,
                                       String source,
                                       String messageType,
                                       String errorCode,
                                       long errorCount) {
        String formattedGroupCreatedDateTime = DATE_TIME_FORMATTER.format(groupCreatedDatetime);
        Map<String, String> parameters = Map.of(IssueSummaryParameters.GROUP_ID.parameterName(), groupId,
                IssueSummaryParameters.GROUP_CREATED_DATETIME.parameterName(), formattedGroupCreatedDateTime,
                IssueSummaryParameters.SOURCE.parameterName(), source,
                IssueSummaryParameters.MESSAGE_TYPE.parameterName(), messageType,
                IssueSummaryParameters.ERROR_CODE.parameterName(), errorCode,
                IssueSummaryParameters.ERROR_COUNT.parameterName(), Long.toString(errorCount));
        return placeholderHelper.replacePlaceholders(ehgConfigProperties.getIssueTracking().getIssueSummaryTemplate(), parameters::get);
    }

    @PostConstruct
    void validateConfiguration() {
        if (ehgConfigProperties.getIssueTracking() != null) {
            try {
                generateIssueSummary("groupId", ZonedDateTime.now(), "source", "messageType", "errorCode", 1);
            } catch (Exception e) {
                throw new IllegalArgumentException("Issue summary template is invalid: " +
                        ehgConfigProperties.getIssueTracking().getIssueSummaryTemplate(), e);
            }
        }
    }

}
