package ch.admin.bit.jeap.errorhandling;

import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorGroup;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Browser tests for the error groups view and editing of the group ticket number / free text.
 */
class UiErrorGroupsBrowserIT extends UiBrowserTestBase {

    @Test
    void errorGroups_showsSeededErrorGroupsWithAggregatedValues() {
        ErrorGroup groupWithTicket = errorGroup("hash-group-1", "JEAP-2222");
        saveError("grouped error one", groupWithTicket);
        saveError("grouped error two", groupWithTicket);
        saveError("grouped error three", errorGroup("hash-group-2", null));

        page.navigate(APP_URL + "error-group");

        assertThat(page.locator("table tbody tr")).hasCount(2);
        Locator rowWithTicket = page.locator("table tbody tr")
                .filter(new Locator.FilterOptions().setHasText("JEAP-2222"));
        assertThat(rowWithTicket).hasCount(1);
        // error count, message type, publisher, error code and stack trace hash of the group
        assertThat(rowWithTicket).containsText("2");
        assertThat(rowWithTicket).containsText("eventName");
        assertThat(rowWithTicket).containsText("service");
        assertThat(rowWithTicket).containsText("123");
        assertThat(rowWithTicket).containsText("hash-group-1");
    }

    @Test
    void errorGroupDetails_updatesTicketNumberAndFreeText() {
        saveError("group edit error", errorGroup("hash-edit", null));

        page.navigate(APP_URL + "error-group");
        assertThat(page.locator("table tbody tr")).hasCount(1);
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(UiLabels.details())).first().click();
        page.waitForURL("**/error-group-details/**");

        // the ticket number is saved on enter
        Locator ticketInput = page.getByLabel(UiLabels.jiraTicket());
        ticketInput.fill("JEAP-7777");
        ticketInput.press("Enter");
        assertThat(page.getByText(UiLabels.ticketNumberUpdated())).isVisible();
        await("ticket number persisted").atMost(FORTY_SECONDS).until(() ->
                errorGroupRepository.existsByTicketNumber("JEAP-7777"));

        // the free text is saved with its own button
        page.getByLabel(UiLabels.freeText()).fill("free text from e2e test");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(UiLabels.saveFreeText())).click();
        assertThat(page.getByText(UiLabels.freeTextSaved())).isVisible();
        await("free text persisted").atMost(FORTY_SECONDS).until(() ->
                errorGroupRepository.findAll().stream()
                        .anyMatch(group -> "free text from e2e test".equals(group.getFreeText())));
    }
}
