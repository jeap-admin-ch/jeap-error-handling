package ch.admin.bit.jeap.errorhandling.domain.group;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IssueDescriptionGeneratorTest {

    private static final String TEST_ERROR_CODE = "test-error-code";
    private static final String TEST_SERVICE = "test-service";
    private static final String TEST_EVENT = "TestEvent";
    private static final String TEST_MESSAGE = "test message";
    private static final UUID TEST_GROUP_ID = UUID.randomUUID();
    private static final ZonedDateTime TEST_GROUP_CREATED_AT = ZonedDateTime.now();
    private static final String TEST_GROUP_URL_TEMPLATE = "https://ehs.local/groups/" + IssueDescriptionGenerator.GROUP_URL_ID_PLACEHOLDER;

    @Test
    void generateDescription() {
        final ErrorGroupConfigProperties errorGroupConfigProperties = new ErrorGroupConfigProperties();
        final ErrorGroupIssueTrackingProperties issueTrackingProperties = new ErrorGroupIssueTrackingProperties();
        errorGroupConfigProperties.setIssueTracking(issueTrackingProperties);
        issueTrackingProperties.setErrorHandlingServiceGroupUrlTemplate(TEST_GROUP_URL_TEMPLATE);
        final IssueDescriptionGenerator generator = new IssueDescriptionGenerator(errorGroupConfigProperties);
        final ErrorGroupAggregatedData errorGroupAggregatedData = createErrorGroupAggregatedData();

        final String description = generator.generateDescription(errorGroupAggregatedData);

        assertThat(description).isEqualTo(getExpectedDescription());
    }

    private static String getExpectedDescription() {
        final String expectedGroupId = TEST_GROUP_ID.toString();
        final String expectedGroupUrl = TEST_GROUP_URL_TEMPLATE
                .substring(0, TEST_GROUP_URL_TEMPLATE.lastIndexOf(IssueDescriptionGenerator.GROUP_URL_ID_PLACEHOLDER))
                + expectedGroupId;
        final String expectedFormattedGroupCreatedAt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG).format(TEST_GROUP_CREATED_AT);
        return """
                The error handling service detected a group of errors:
                   ||_Error Group Property_         ||_Data_ ||
                   |  Error Group Id          |  *[%1$s|%2$s]*  |
                   |  Error Source            |  *%3$s*  |
                   |  Error Message Type      |  *%4$s*  |
                   |  Error Code              |  *%5$s*  |
                   |  Error Message           |  *%6$s*  |
                   |  Error Group Created At  |  *%7$s*  |
                """.formatted(
                expectedGroupId,
                expectedGroupUrl,
                "test-service",
                "TestEvent",
                "test-error-code",
                "test message",
                expectedFormattedGroupCreatedAt);
    }

    private ErrorGroupAggregatedData createErrorGroupAggregatedData() {
        return ErrorGroupAggregatedDataRecord.builder()
                .groupId(TEST_GROUP_ID)
                .groupCreatedAt(TEST_GROUP_CREATED_AT)
                .errorCode(TEST_ERROR_CODE)
                .errorEvent(TEST_EVENT)
                .errorPublisher(TEST_SERVICE)
                .errorMessage(TEST_MESSAGE)
                .build();
    }

}
