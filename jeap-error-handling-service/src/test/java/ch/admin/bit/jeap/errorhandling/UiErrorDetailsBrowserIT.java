package ch.admin.bit.jeap.errorhandling;

import ch.admin.bit.jeap.errorhandling.event.test.TestEvent;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Browser tests for the error details view and the retry / delete actions.
 */
class UiErrorDetailsBrowserIT extends UiBrowserTestBase {

    @Test
    void errorDetails_showsDeserializedAvroPayloadAsJson() {
        publishPermanentErrorTestEvent();
        awaitErrorsInRepository(1);

        page.navigate(APP_URL + "error-list");
        assertThat(page.locator("table tbody tr")).hasCount(1);
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(UiLabels.details())).first().click();
        page.waitForURL("**/error-details/**");

        // metadata of the causing event and the error
        assertThat(page.getByText("TestEvent").first()).isVisible();
        assertThat(page.getByText("jeap-error-handling-service").first()).isVisible();
        assertThat(page.getByText(DOMAIN_EVENT_TOPIC).first()).isVisible();

        // the original Avro payload of the causing event is deserialized and displayed as JSON
        assertThat(page.getByTestId("causing-event-payload")).containsText("\"message\"");
        assertThat(page.getByTestId("causing-event-payload")).containsText(PERMANENT_ERROR);
        assertThat(page.getByTestId("causing-event-payload")).containsText("\"identity\"");
    }

    @Test
    void errorDetails_showsFallbackJsonForNonAvroPayload() {
        saveError("non-avro payload error", null);

        page.navigate(APP_URL + "error-list");
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(UiLabels.details())).first().click();
        page.waitForURL("**/error-details/**");

        assertThat(page.getByText("non-avro payload error").first()).isVisible();
        assertThat(page.getByTestId("causing-event-payload")).containsText("Deserialization failed");
    }

    @Test
    void errorRetry_resendsCausingEvent_andRecordsAuditLog() {
        TestEvent testEvent = publishPermanentErrorTestEvent();
        Error error = awaitErrorsInRepository(1).getFirst();

        page.navigate(APP_URL + "error-details/" + error.getId());
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(UiLabels.retryEvent())).click();
        assertThat(page.getByText(UiLabels.success()).first()).isVisible();

        // the resend scheduler republishes the causing event, the test consumer receives it a second time
        await("causing event consumed again").atMost(FORTY_SECONDS).until(() ->
                testConsumer.getConsumedEventsByIdempotenceId(testEvent.getIdentity().getIdempotenceId()).size() == 2);
        await("error state moved to retried").atMost(FORTY_SECONDS).until(() ->
                errorRepository.findById(error.getId()).orElseThrow().getState() == Error.ErrorState.PERMANENT_RETRIED);
        // the resent message fails again on purpose, deterministically producing a new permanent error
        awaitErrorsInRepository(2);

        // the retry is recorded in the audit log shown on the details page, with the user from the token claims
        page.navigate(APP_URL + "error-details/" + error.getId());
        assertThat(page.getByText(UiLabels.auditResendCausingEvent())).isVisible();
        assertThat(page.getByText("E2E").first()).isVisible();
        assertThat(page.getByText("Testuser").first()).isVisible();

        // the retried error leaves the default PERMANENT list filter, only the new failure remains
        page.navigate(APP_URL + "error-list");
        assertThat(page.locator("table tbody tr")).hasCount(1);
        assertThat(page.locator("a[href*='" + error.getId() + "']")).hasCount(0);
    }

    @Test
    void errorDelete_withClosingReason_movesErrorToDeletedState() {
        Error error = saveError("error to delete", null);

        page.navigate(APP_URL + "error-details/" + error.getId());
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(UiLabels.ignoreError())).click();

        assertThat(page.getByText(UiLabels.closingReasonPrompt())).isVisible();
        page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName(UiLabels.closingReason())).fill("e2e closing reason");
        confirmDialog();
        assertThat(page.getByText(UiLabels.success()).first()).isVisible();

        await("error state moved to deleted").atMost(FORTY_SECONDS).until(() ->
                errorRepository.findById(error.getId()).orElseThrow().getState().name().startsWith("DELETE"));

        // deletion is recorded in the audit log with the closing reason
        page.navigate(APP_URL + "error-details/" + error.getId());
        assertThat(page.getByText(UiLabels.auditCloseError())).isVisible();
        assertThat(page.getByText("e2e closing reason")).isVisible();

        // deleted errors leave the default PERMANENT list filter
        page.navigate(APP_URL + "error-list");
        assertThat(page.locator("table tbody tr")).hasCount(0);
    }

    @Test
    void errorList_navigatesToErrorGroupOfError() {
        saveError("grouped error", errorGroup("hash-nav", "JEAP-3333"));

        page.navigate(APP_URL + "error-list");
        assertThat(page.locator("table tbody tr")).hasCount(1);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(UiLabels.goToErrorGroup())).click();
        page.waitForURL("**/error-group-details/**");

        assertThat(page.getByText(UiLabels.errorGroupDetails())).isVisible();
        assertThat(page.getByLabel(UiLabels.jiraTicket())).hasValue("JEAP-3333");
        assertThat(page.getByText("grouped error").first()).isVisible();
    }
}
