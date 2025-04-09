package ch.admin.bit.jeap.errorhandling.web.ui.configuration;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
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
     * Should PAMS mock be used.
     */
    @NotNull
    private Boolean mockPams;
    /**
     * Pams Environment to be used.
     */
    @NotEmpty
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
}
