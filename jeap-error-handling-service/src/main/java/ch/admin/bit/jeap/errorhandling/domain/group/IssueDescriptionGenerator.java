package ch.admin.bit.jeap.errorhandling.domain.group;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.PropertyPlaceholderHelper;

import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class IssueDescriptionGenerator {

    static final String GROUP_URL_ID_PLACEHOLDER = "{groupId}";
    private static final String DESCRIPTION_TEMPLATE = """
            The error handling service detected a group of errors:
               ||_Error Group Property_         ||_Data_ ||
               |  Error Group Id          |  *[${group-id}|${group-url}]*  |
               |  Error Source            |  *${source}*  |
               |  Error Message Type      |  *${message-type}*  |
               |  Error Code              |  *${code}*  |
               |  Error Message           |  *${message}*  |
               |  Error Group Created At  |  *${group-created-at}*  |
            """;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG);

    private final ErrorGroupConfigProperties ehgConfigProperties;
    private final PropertyPlaceholderHelper placeholderHelper = new PropertyPlaceholderHelper(
            "${", "}", null, null, false);

    public String generateDescription(ErrorGroupAggregatedData egData) {
        final String formattedGroupCreatedDateTime = DATE_TIME_FORMATTER.format(egData.getGroupCreatedAt());
        final String groupIdString = egData.getGroupId().toString();
        final String groupUrl = ehgConfigProperties.getIssueTracking().getErrorHandlingServiceGroupUrlTemplate()
                .replace(GROUP_URL_ID_PLACEHOLDER, groupIdString);
        Map<String, String> descriptionData = Map.of(
                "group-id", groupIdString,
                "source", egData.getErrorPublisher(),
                "message-type", egData.getErrorEvent(),
                "code", egData.getErrorCode(),
                "message", egData.getErrorMessage(),
                "group-created-at", formattedGroupCreatedDateTime,
                "group-url", groupUrl);
        return placeholderHelper.replacePlaceholders(DESCRIPTION_TEMPLATE, descriptionData::get);
    }

}
