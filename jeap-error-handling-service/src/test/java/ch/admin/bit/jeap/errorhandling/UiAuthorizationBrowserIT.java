package ch.admin.bit.jeap.errorhandling;

import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Browser tests for role based authorization in the UI: route guards and action visibility.
 */
class UiAuthorizationBrowserIT extends UiBrowserTestBase {

    @Test
    void errorList_withoutErrorRoles_redirectsToForbidden() {
        openPageWithRoles(UNRELATED_ROLES);

        page.navigate(APP_URL + "error-list");

        page.waitForURL("**/Forbidden");
    }

    @Test
    void errorGroups_withoutErrorGroupRole_redirectsToForbidden() {
        openPageWithRoles(VIEW_ONLY_ROLES);

        page.navigate(APP_URL + "error-group");

        page.waitForURL("**/Forbidden");
    }

    @Test
    void errorDetails_withViewOnlyRole_hidesRetryAndDeleteActions() {
        Error error = saveError("view only error", null);

        // with all roles, retry and delete actions are offered on the details page
        page.navigate(APP_URL + "error-details/" + error.getId());
        assertThat(retryButton()).isVisible();
        assertThat(ignoreButton()).isVisible();

        // with the view role only, the server reports the error as neither retryable nor deletable
        openPageWithRoles(VIEW_ONLY_ROLES);
        page.navigate(APP_URL + "error-details/" + error.getId());
        assertThat(page.getByText("view only error").first()).isVisible();
        assertThat(retryButton()).not().isVisible();
        assertThat(ignoreButton()).not().isVisible();
    }

    private Locator retryButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(UiLabels.retryEvent()));
    }

    private Locator ignoreButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(UiLabels.ignoreError()));
    }
}
