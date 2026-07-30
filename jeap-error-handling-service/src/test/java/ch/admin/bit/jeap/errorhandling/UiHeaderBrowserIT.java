package ch.admin.bit.jeap.errorhandling;

import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Browser tests for the application header with the PAMS integration disabled, which is how the tests are
 * configured (jeap.errorhandling.frontend.pams-enabled=false in the test application.yml).
 */
class UiHeaderBrowserIT extends UiBrowserTestBase {

    @Test
    void headerWithPamsDisabled_doesNotContactEportal() {
        page.navigate(APP_URL + "error-list");

        // wait for the application to be fully loaded before asserting on the requests it has sent
        assertThat(languageSelection()).isVisible();

        org.assertj.core.api.Assertions.assertThat(ePortalRequests).isEmpty();
    }

    @Test
    void headerWithPamsDisabled_hidesLoginAndProfileButKeepsLanguageSelection() {
        page.navigate(APP_URL + "error-list");

        assertThat(languageSelection()).isVisible();
        assertThat(loginLink()).not().isVisible();
        assertThat(profileButton()).not().isVisible();
    }

    /**
     * The language selection of Oblique's service navigation. Its label is rendered by Oblique itself and is
     * therefore not part of the application's i18n files, so the element id assigned by Oblique is used.
     */
    private Locator languageSelection() {
        return page.locator("#ob-language-dropdown");
    }

    /**
     * The login link of Oblique's service navigation, which without a reachable ePortal backend would be
     * rendered in a disabled state. Located by the element id assigned by Oblique, see languageSelection().
     */
    private Locator loginLink() {
        return page.locator("#ob-service-navigation-authentication-link-to-login");
    }

    /**
     * The profile button of Oblique's service navigation. Located by the element id assigned by Oblique, see
     * languageSelection().
     */
    private Locator profileButton() {
        return page.locator("#service-navigation-toggle-profile-icon-button");
    }
}
