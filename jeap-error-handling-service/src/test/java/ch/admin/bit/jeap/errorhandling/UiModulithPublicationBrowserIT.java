package ch.admin.bit.jeap.errorhandling;

import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Browser tests for the use case path of a failed Spring Modulith publication: unlike a failed Kafka message, it has
 * no topic and no Avro payload, its metadata is the publication of one listener, and its actions ask the publishing
 * application to retry or discard the publication instead of resending a message.
 */
class UiModulithPublicationBrowserIT extends UiBrowserTestBase {

    private static final String PUBLICATION_PAYLOAD = """
            {"orderId":"4711","customer":"Muster","positions":[{"article":"A-1","amount":2}]}""";

    @Test
    void modulithError_detailsShowOriginAndPublicationMetadata() {
        String publicationId = UUID.randomUUID().toString();
        saveModulithError("listener processing exhausted its retries", publicationId, PUBLICATION_PAYLOAD);

        openDetailsOfSingleError();

        // the origin distinguishes a failed publication from a failed Kafka message
        assertThat(page.getByText(UiLabels.modulithPublicationOrigin(), new Page.GetByTextOptions().setExact(true)))
                .isVisible();
        assertThat(page.getByTestId("publication-id")).hasText(publicationId);
        assertThat(page.getByTestId("publication-listener")).hasText(MODULITH_LISTENER);
        assertThat(page.getByTestId("publication-event-type")).hasText(MODULITH_EVENT_TYPE);
        assertThat(page.getByTestId("publication-content-type")).hasText(MODULITH_CONTENT_TYPE);
        // a publication has no Kafka topic, so the topic of the causing message is not shown
        assertThat(page.getByText(UiLabels.eventTopic())).hasCount(0);
    }

    @Test
    void modulithError_detailsShowSerializedEventPayload() {
        saveModulithError("listener processing exhausted its retries", UUID.randomUUID().toString(), PUBLICATION_PAYLOAD);

        openDetailsOfSingleError();

        // the payload of a publication is stored in the form the publishing application serialized it in and is
        // served verbatim, without being deserialized as Avro like the payload of a failed Kafka message
        assertThat(page.getByTestId("causing-event-payload")).hasText(PUBLICATION_PAYLOAD);
    }

    @Test
    void modulithError_retryPublication_queuesRetryCommandAndRecordsAuditLog() {
        Error error = saveModulithError("listener processing exhausted its retries", UUID.randomUUID().toString(), PUBLICATION_PAYLOAD);

        page.navigate(APP_URL + "error-details/" + error.getId());
        page.getByTestId("retry-publication-action").click();
        assertThat(page.getByText(UiLabels.success()).first()).isVisible();

        // the retry command is queued in the transactional outbox and the manual task is only closed afterwards by
        // the task synchronization, so the error waits in RESOLVE_ON_MANUALTASK
        await("error waits for its manual task to be closed").atMost(FORTY_SECONDS).until(() ->
                errorRepository.findById(error.getId()).orElseThrow().getState() == Error.ErrorState.RESOLVE_ON_MANUALTASK);

        page.navigate(APP_URL + "error-details/" + error.getId());
        assertThat(page.getByText(UiLabels.auditResendCausingEvent())).isVisible();
        assertThat(page.getByText("Testuser").first()).isVisible();

        // the retried error leaves the default PERMANENT list filter
        page.navigate(APP_URL + "error-list");
        assertThat(errorListRows()).hasCount(0);
    }

    @Test
    void modulithError_discardPublication_withClosingReason_movesErrorToDeletedState() {
        Error error = saveModulithError("listener processing exhausted its retries", UUID.randomUUID().toString(), PUBLICATION_PAYLOAD);

        page.navigate(APP_URL + "error-details/" + error.getId());
        page.getByTestId("discard-publication-action").click();

        assertThat(page.getByText(UiLabels.closingReasonPrompt())).isVisible();
        page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName(UiLabels.closingReason()))
                .fill("publication is obsolete");
        confirmDialog();
        assertThat(page.getByText(UiLabels.success()).first()).isVisible();

        await("error state moved to deleted").atMost(FORTY_SECONDS).until(() ->
                errorRepository.findById(error.getId()).orElseThrow().getState().name().startsWith("DELETE"));

        page.navigate(APP_URL + "error-details/" + error.getId());
        assertThat(page.getByText(UiLabels.auditCloseError())).isVisible();
        assertThat(page.getByText("publication is obsolete")).isVisible();

        page.navigate(APP_URL + "error-list");
        assertThat(errorListRows()).hasCount(0);
    }

    private void openDetailsOfSingleError() {
        page.navigate(APP_URL + "error-list");
        assertThat(errorListRows()).hasCount(1);
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(UiLabels.details())).click();
        page.waitForURL("**/error-details/**");
    }
}
