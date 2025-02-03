package ch.admin.bit.jeap.errorhandling.web.ui.configuration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class ConfigurationDTO {
    private String applicationUrl;
    private String logoutRedirectUri;
    private boolean mockPams;
    private String pamsEnvironment;
    private List<String> tokenAwarePatterns;
    private String appVersion;
    private String authority;
    private String clientId;
    private boolean useAutoLogin;
    private String redirectUrl;
}
