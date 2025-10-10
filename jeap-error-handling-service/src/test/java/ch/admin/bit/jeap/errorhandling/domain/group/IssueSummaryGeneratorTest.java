package ch.admin.bit.jeap.errorhandling.domain.group;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IssueSummaryGeneratorTest {

    @Test
    void generateIssueSummary_ReplacesAllPlaceholders() {
        final String template = "Ticket for '{group-id}' created at {group-created-datetime} from '{source}' as '{message-type}' with '{error-code}' occurred {error-count} times";
        for (var parameter : IssueSummaryGenerator.IssueSummaryParameters.values()) {
            assertThat(template).contains(parameter.parameterName());
        }
        final ErrorGroupConfigProperties ehgConfigProperties = createErrorGroupConfigProperties(template);
        final IssueSummaryGenerator issueSummaryGenerator = new IssueSummaryGenerator(ehgConfigProperties);
        final ZonedDateTime createdAt = ZonedDateTime.now();
        final String expectedDateTimeFormatted = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).format(createdAt);

        final String issueName = issueSummaryGenerator.generateIssueSummary(
                "test-group-id",
                createdAt,
                "test-service",
                "test-event-type",
                "test-error-code",
                42);

        assertThat(issueName).isEqualTo(
                "Ticket for 'test-group-id' created at %s from 'test-service' as 'test-event-type' with 'test-error-code' occurred 42 times"
                        .formatted(expectedDateTimeFormatted));
    }

    @Test
    void generateIssueSummary_onlySomePlaceholdersUsed() {
        final ErrorGroupConfigProperties ehgConfigProperties =
                createErrorGroupConfigProperties("Processing of '{message-type}' from '{source}' fails with '{error-code}'");
        final IssueSummaryGenerator issueSummaryGenerator = new IssueSummaryGenerator(ehgConfigProperties);

        final String issueName = issueSummaryGenerator.generateIssueSummary(
                "unused",
                ZonedDateTime.now(),
                "test-service",
                "test-event-type",
                "test-error-code",
                0);

        assertThat(issueName).isEqualTo("Processing of 'test-event-type' from 'test-service' fails with 'test-error-code'");
    }

    @Test
    void validateConfiguration_unknownPlaceholderThrowsIllegalArgumentException() {
        final ErrorGroupConfigProperties ehgConfigProperties =
                createErrorGroupConfigProperties("Ticket for {unknown-placeholder}");
        final IssueSummaryGenerator issueSummaryGenerator = new IssueSummaryGenerator(ehgConfigProperties);

        assertThatThrownBy(issueSummaryGenerator::validateConfiguration)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Issue summary template is invalid: Ticket for {unknown-placeholder}");
    }

    private ErrorGroupConfigProperties createErrorGroupConfigProperties(String issueSummaryTemplate) {
        final ErrorGroupConfigProperties errorGroupConfigProperties = new ErrorGroupConfigProperties();
        final ErrorGroupIssueTrackingProperties issueTrackingProperties = new ErrorGroupIssueTrackingProperties();
        issueTrackingProperties.setIssueSummaryTemplate(issueSummaryTemplate);
        errorGroupConfigProperties.setIssueTracking(issueTrackingProperties);
        return errorGroupConfigProperties;
    }

}
