package ch.admin.bit.jeap.errorhandling.web.ui.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration properties that will be forwarded to the UI
 */
@Configuration
@ConfigurationProperties(prefix = "jeap.errorhandling.frontend")
@Data
public class FrontendConfigProperties {
    /**
     * Authentication server to be used.
     */
    private String stsServer;
    /**
     * URL of the application for the redirect URI after a login.
     */
    private String applicationUrl;
    /**
     * URL to go to after a logout.
     */
    private String logoutRedirectUri;
    /**
     * Should PAMS mock be used.
     */
    private boolean mockPams;
    /**
     * Pams Environment to be used.
     */
    private String pamsEnvironment;
    /**
     * List of backends where to a token shall be send.
     */
    private List<String> tokenAwarePattern;
    /**
     * Oidc client id
     */
    private String clientId;
    /**
     * Should silent renew be used (currently only >= REF)
     */
    private boolean silentRenew;
    /**
     * Default system name for authorization filter
     */
    private String systemName;
    /**
     * Should automatically login, when PAMS session is not active
     */
    private boolean autoLogin;
    /**
     * Should new claim be submitted after token was renewed (e.g. silent renew)
     */
    private boolean renewUserInfoAfterTokenRenew;
    /**
     * URL to redirect user after login
     */
    private String redirectUrl;
    /**
     * URL to display ticker number
     */
    private String ticketingSystemUrl;
}
