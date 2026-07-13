package ch.admin.bit.jeap.errorhandling;

import ch.admin.bit.jeap.errorhandling.event.test.TestEvent;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Browser tests for the error list view: search filters, mass actions and persisted view settings.
 */
class UiErrorListBrowserIT extends UiBrowserTestBase {

    @Test
    void errorList_showsSeededErrors_andNoTicketFilterHidesTicketedErrors() {
        saveError("error with ticket", errorGroup("hash-ticket", "JEAP-1111"));
        saveError("error in group without ticket", errorGroup("hash-no-ticket", null));
        saveError("error without group", null);

        page.navigate(APP_URL + "error-list");

        assertThat(page.locator("table tbody tr")).hasCount(3);
        assertThat(page.getByText("error with ticket")).isVisible();

        noTicketCheckbox().check();
        searchButton().click();

        assertThat(page.locator("table tbody tr")).hasCount(2);
        assertThat(page.getByText("error with ticket")).not().isVisible();
        assertThat(page.getByText("error in group without ticket")).isVisible();
        assertThat(page.getByText("error without group")).isVisible();
    }

    @Test
    void errorList_noTicketFilterState_isPersistedAcrossReload() {
        saveError("some error", null);

        page.navigate(APP_URL + "error-list");
        assertThat(page.locator("table tbody tr")).hasCount(1);
        noTicketCheckbox().check();
        assertThat(noTicketCheckbox()).isChecked();

        page.navigate(APP_URL + "error-list");

        assertThat(noTicketCheckbox()).isChecked();
    }

    @Test
    void errorList_filtersByErrorCodeAndSource() {
        saveError("error from service-a", null, "CODE-A", "service-a", ZonedDateTime.now());
        saveError("error from service-b", null, "CODE-B", "service-b", ZonedDateTime.now());

        page.navigate(APP_URL + "error-list");
        assertThat(page.locator("table tbody tr")).hasCount(2);

        selectDropDownOption(UiLabels.errorCodeFilter(), "CODE-A");
        searchButton().click();
        assertThat(page.locator("table tbody tr")).hasCount(1);
        assertThat(page.getByText("error from service-a")).isVisible();

        // reset clears the error code filter, then filter by source
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(UiLabels.reset())).click();
        selectDropDownOption(UiLabels.sourceFilter(), "service-b");
        searchButton().click();
        assertThat(page.locator("table tbody tr")).hasCount(1);
        assertThat(page.getByText("error from service-b")).isVisible();
    }

    @Test
    void errorList_massDelete_deletesAllSelectedErrors() {
        saveError("mass delete one", null);
        saveError("mass delete two", null);
        saveError("mass delete three", null);

        page.navigate(APP_URL + "error-list");
        assertThat(page.locator("table tbody tr")).hasCount(3);

        // select all rows via the header checkbox, then use the multi-delete header action
        page.getByRole(AriaRole.CHECKBOX, new Page.GetByRoleOptions().setName(UiLabels.selectAllErrors())).check();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(UiLabels.deleteSelectedErrors())).click();

        // confirm dialog, then closing reason dialog
        assertThat(page.getByText(UiLabels.confirmEditCount(3))).isVisible();
        confirmDialog();
        page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName(UiLabels.closingReason())).fill("mass delete e2e");
        confirmDialog();

        assertThat(page.locator("table tbody tr")).hasCount(0);
        await("all errors deleted").atMost(FORTY_SECONDS).until(() ->
                errorRepository.findAll().stream().allMatch(error -> error.getState() == Error.ErrorState.DELETED));
    }

    @Test
    void errorList_massRetry_resendsAllSelectedCausingEvents() {
        TestEvent eventOne = publishPermanentErrorTestEvent();
        TestEvent eventTwo = publishPermanentErrorTestEvent();
        List<Error> originalErrors = awaitErrorsInRepository(2);

        page.navigate(APP_URL + "error-list");
        assertThat(page.locator("table tbody tr")).hasCount(2);

        page.getByRole(AriaRole.CHECKBOX, new Page.GetByRoleOptions().setName(UiLabels.selectAllErrors())).check();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(UiLabels.resendSelectedErrors())).click();
        assertThat(page.getByText(UiLabels.confirmEditCount(2))).isVisible();
        confirmDialog();

        // the resend scheduler republishes the causing events, the test consumer receives them a second time
        await("both causing events consumed again").atMost(FORTY_SECONDS).until(() ->
                testConsumer.getConsumedEventsByIdempotenceId(eventOne.getIdentity().getIdempotenceId()).size() == 2 &&
                        testConsumer.getConsumedEventsByIdempotenceId(eventTwo.getIdentity().getIdempotenceId()).size() == 2);
        // the resent messages fail again on purpose, deterministically producing two new permanent errors
        awaitErrorsInRepository(4);

        // the retried errors leave the default PERMANENT filter, only the two new failures remain
        page.navigate(APP_URL + "error-list");
        assertThat(page.locator("table tbody tr")).hasCount(2);
        originalErrors.forEach(error ->
                assertThat(page.locator("a[href*='" + error.getId() + "']")).hasCount(0));
    }

    @Test
    void errorList_sortAndPageSize_arePersistedAcrossReload() {
        for (int i = 1; i <= 6; i++) {
            saveError("error number " + i, null, "123", "service", ZonedDateTime.now().minusDays(i));
        }

        page.navigate(APP_URL + "error-list");
        assertThat(page.locator("table tbody tr")).hasCount(6);

        // change the page size to 5 and sort by timestamp ascending (default is descending)
        page.locator("mat-paginator").getByRole(AriaRole.COMBOBOX).click();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName("5").setExact(true)).click();
        assertThat(page.locator("table tbody tr")).hasCount(5);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(UiLabels.timestampColumn())).click();
        assertThat(page.locator("table tbody tr").first()).containsText("error number 6");

        page.navigate(APP_URL + "error-list");

        // both settings are restored from local storage
        assertThat(page.locator("table tbody tr")).hasCount(5);
        assertThat(page.locator("table tbody tr").first()).containsText("error number 6");
    }

    private Locator searchButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(UiLabels.search()));
    }

    private Locator noTicketCheckbox() {
        return page.getByRole(AriaRole.CHECKBOX, new Page.GetByRoleOptions().setName(UiLabels.noJiraTicket()));
    }

    private void selectDropDownOption(String dropDownLabel, String optionText) {
        page.getByRole(AriaRole.COMBOBOX, new Page.GetByRoleOptions().setName(dropDownLabel)).click();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(optionText)).click();
    }
}
