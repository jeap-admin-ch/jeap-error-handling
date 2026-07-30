package ch.admin.bit.jeap.errorhandling.web.ui.configuration;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties that will be forwarded to the UI
 */
@Configuration
@ConfigurationProperties(prefix = "jeap.errorhandling.frontend")
@Data
@Validated
@Slf4j
public class FrontendConfigProperties {
    /**
     * Authentication server to be used.
     */
    @NotEmpty
    private String stsServer;
    /**
     * URL of the application for the redirect URI after a login.
     */
    @NotEmpty
    private String applicationUrl;
    /**
     * URL to go to after a logout.
     */
    @NotEmpty
    private String logoutRedirectUri;
    /**
     * Is the application integrated with PAMS/ePortal. Set to false for deployments without PAMS: the ePortal
     * service navigation of the UI header is then not contacted at all and its PAMS-backed controls are hidden.
     */
    @NotNull
    private Boolean pamsEnabled = true;
    /**
     * Should PAMS mock be used. Implied when PAMS is disabled, see {@link #isMockPamsEffective()}.
     */
    @NotNull
    private Boolean mockPams = false;
    /**
     * Pams Environment to be used. Required unless PAMS is disabled.
     */
    private String pamsEnvironment;
    /**
     * List of backends where to a token shall be send.
     */
    private List<String> tokenAwarePattern = new ArrayList<>();
    /**
     * Oidc client id
     */
    @NotEmpty
    private String clientId;
    /**
     * Should silent renew be used (currently only >= REF)
     */
    @NotNull
    private Boolean silentRenew;
    /**
     * Default system name for authorization filter
     */
    @NotEmpty
    private String systemName;
    /**
     * Should automatically login, when PAMS session is not active
     */
    @NotNull
    private Boolean autoLogin;
    /**
     * Should new claim be submitted after token was renewed (e.g. silent renew)
     */
    @NotNull
    private Boolean renewUserInfoAfterTokenRenew;
    /**
     * URL to redirect user after login
     */
    @NotEmpty
    private String redirectUrl;
    /**
     * URL to display ticker number
     */
    @NotEmpty
    private String ticketingSystemUrl;

    @AssertTrue(message = "jeap.errorhandling.frontend.pams-environment must be set unless " +
            "jeap.errorhandling.frontend.pams-enabled is false")
    boolean isPamsEnvironmentSetWhenPamsEnabled() {
        return Boolean.FALSE.equals(pamsEnabled) || (pamsEnvironment != null && !pamsEnvironment.isEmpty());
    }

    /**
     * Whether the UI should treat the PAMS session as always active instead of reading it from the ePortal
     * service navigation. Disabling PAMS implies mocking it: there is no PAMS session to check, and without
     * this the UI would wait forever for a login state the service navigation never reports.
     */
    public boolean isMockPamsEffective() {
        return Boolean.FALSE.equals(pamsEnabled) || Boolean.TRUE.equals(mockPams);
    }

    /**
     * The PAMS environment to serve to the UI, {@code null} if PAMS is disabled. A configured environment is
     * deliberately not passed on in that case, as it would make the UI contact the ePortal backend again.
     */
    public String getEffectivePamsEnvironment() {
        return Boolean.FALSE.equals(pamsEnabled) ? null : pamsEnvironment;
    }

    @PostConstruct
    void logPamsConfiguration() {
        if (Boolean.FALSE.equals(pamsEnabled)) {
            log.info("PAMS integration is disabled: the UI will not contact the ePortal service navigation, " +
                    "will hide the header controls served by PAMS and will treat the PAMS session as mocked.");
        }
    }
}
