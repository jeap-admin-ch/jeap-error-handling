package ch.admin.bit.jeap.errorhandling.web.ui.configuration;

import ch.admin.bit.jeap.errorhandling.domain.group.ErrorGroupConfigProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Configuration")
@RestController
@RequestMapping("/api/configuration")
@RequiredArgsConstructor
@Slf4j
class ConfigurationController {

    private final FrontendConfigProperties frontendConfigProperties;

    private final LogDeepLinkProperties logDeepLinkProperties;

    private final ErrorGroupConfigProperties errorGroupConfigProperties;


    @Value("${jeap.security.oauth2.resourceserver.authorization-server.issuer}")
    private String authority;

    @Schema(description = "Returns the frontend configuration")
    @GetMapping
    public ConfigurationDTO getAuthConfiguration() {
        return ConfigurationDTO.builder()
                .applicationUrl(frontendConfigProperties.getApplicationUrl())
                .pamsEnvironment(frontendConfigProperties.getPamsEnvironment())
                .logoutRedirectUri(frontendConfigProperties.getLogoutRedirectUri())
                .mockPams(frontendConfigProperties.getMockPams())
                .tokenAwarePatterns(frontendConfigProperties.getTokenAwarePattern())
                .appVersion(getVersion())
                .authority(authority)
                .clientId(frontendConfigProperties.getClientId())
                .useAutoLogin(frontendConfigProperties.getAutoLogin())
                .redirectUrl(frontendConfigProperties.getRedirectUrl())
                .build();
    }

    @Schema(description = "Returns the Version of the ErrorHandlingService")
    @GetMapping("/version")
    public String getVersion() {
        VersionDetector versionDetector = new VersionDetector();
        return versionDetector.getVersion();
    }

    @Schema(description = "Returns the custom Log deeplink template")
    @GetMapping("/log-deeplink")
    public String getLogDeepLink() {
        return logDeepLinkProperties.getBaseUrl();
    }

    @Schema(description = "Returns the error group handling configuration.")
    @GetMapping("/error-group")
    public ErrorGroupConfigurationDTO getErrorGroupConfiguration() {
        return ErrorGroupConfigurationDTO.builder()
                .ticketingSystemUrl(frontendConfigProperties.getTicketingSystemUrl())
                .issueTrackingEnabled(errorGroupConfigProperties.isIssueTrackingEnabled())
                .build();
    }
}
