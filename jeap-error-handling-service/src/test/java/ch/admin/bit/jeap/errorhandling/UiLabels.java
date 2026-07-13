package ch.admin.bit.jeap.errorhandling;

import lombok.experimental.UtilityClass;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Resolves the visible UI labels used by the browser tests from the application's English i18n file, by their
 * i18n key. The browser tests run with the {@code en-US} locale, so the labels are looked up in {@code en.json}.
 */
@UtilityClass
public class UiLabels {

    private static final String EN_JSON_RESOURCE = "/static/assets/i18n/en.json";
    private static final Map<String, String> TRANSLATIONS = loadTranslations();

    // --- error list view ---------------------------------------------------------------------------------

    public static String search() {
        return resolve("i18n.errorhandling.search");
    }

    public static String reset() {
        return resolve("i18n.errorhandling.reset");
    }

    public static String noJiraTicket() {
        return resolve("i18n.error.filter.noTicket");
    }

    public static String errorCodeFilter() {
        return resolve("i18n.errorhandling.form.errorcode");
    }

    public static String sourceFilter() {
        return resolve("i18n.errorhandling.form.source");
    }

    public static String selectAllErrors() {
        return resolve("i18n.errorhandling.list.select-all");
    }

    public static String resendSelectedErrors() {
        return resolve("i18n.errorhandling.list.multi-resend");
    }

    public static String deleteSelectedErrors() {
        return resolve("i18n.errorhandling.list.multi-delete");
    }

    public static String timestampColumn() {
        return resolve("i18n.error.timestamp");
    }

    public static String confirmEditCount(int count) {
        return resolve("i18n.errorhandling.confirm").replace("{{count}}", Integer.toString(count));
    }

    // --- error details view ------------------------------------------------------------------------------

    public static String details() {
        return resolve("i18n.errorhandling.details");
    }

    public static String retryEvent() {
        return resolve("i18n.errorhandling.action.retry");
    }

    public static String ignoreError() {
        return resolve("i18n.errorhandling.action.delete");
    }

    public static String success() {
        return resolve("i18n.errorhandling.action.success");
    }

    public static String auditResendCausingEvent() {
        return resolve("i18n.error.audit-log.action.RESEND_CAUSING_EVENT");
    }

    public static String auditCloseError() {
        return resolve("i18n.error.audit-log.action.DELETE_ERROR");
    }

    public static String goToErrorGroup() {
        return resolve("i18n.goToErrorGroup");
    }

    // --- dialogs -----------------------------------------------------------------------------------------

    public static String confirm() {
        return resolve("i18n.errorhandling.dialog.submit");
    }

    public static String closingReason() {
        return resolve("i18n.errorhandling.form.closing-reason");
    }

    public static String closingReasonPrompt() {
        return resolve("i18n.errorhandling.closing-dialog.prompt");
    }

    // --- error group view --------------------------------------------------------------------------------

    public static String errorGroupDetails() {
        return resolve("i18n.errorGroupDetails");
    }

    public static String jiraTicket() {
        return resolve("i18n.jiraTicket");
    }

    public static String ticketNumberUpdated() {
        return resolve("i18n.updateTicketNumberSuccess");
    }

    public static String freeText() {
        return resolve("i18n.error.timestamp.freeText");
    }

    public static String saveFreeText() {
        return resolve("i18n.saveFreeText");
    }

    public static String freeTextSaved() {
        return resolve("i18n.saveFreeTextSuccess");
    }

    // --- resolution --------------------------------------------------------------------------------------

    private static String resolve(String key) {
        String value = TRANSLATIONS.get(key);
        if (value == null) {
            throw new IllegalStateException("i18n key not found in " + EN_JSON_RESOURCE + ": " + key);
        }
        return value;
    }

    private static Map<String, String> loadTranslations() {
        try (InputStream in = UiLabels.class.getResourceAsStream(EN_JSON_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("UI translations not found on the classpath at " + EN_JSON_RESOURCE
                        + " - build the jeap-error-handling-ui module first.");
            }
            return new JsonMapper().readValue(in, new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read UI translations from " + EN_JSON_RESOURCE, e);
        }
    }
}
