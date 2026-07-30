package ch.admin.bit.jeap.errorhandling.web.ui.configuration;

import ch.admin.bit.jeap.errorhandling.domain.group.ErrorGroupConfigProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.List;

@WebMvcTest(ConfigurationController.class)
@ActiveProfiles("error-controller-test")
@ContextConfiguration(classes = ConfigurationControllerTest.TestConfiguration.class)
class ConfigurationControllerTest {

    private static final String PROFILE = "error-controller-test";
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private LogDeepLinkProperties logDeepLinkProperties;
    @MockitoBean
    private FrontendConfigProperties frontendConfigProperties;
    @MockitoBean
    private ErrorGroupConfigProperties errorGroupConfigProperties;
    @MockitoBean
    private ErrorListConfigProperties errorListConfigProperties;

    @Test
    void getLogDeepLink() throws Exception {
        String expectedTemplate = "https://log-system.example.com/en/app/myapp/search?q=error";
        Mockito.when(logDeepLinkProperties.getBaseUrl()).thenReturn(expectedTemplate);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/configuration/log-deeplink"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(expectedTemplate));
    }

    @Test
    void getErrorGroupConfiguration() throws Exception {
        final String jiraUrl = "https://someJiraUrl/browse/JIRA-007";
        final boolean issueTrackingEnabled = true;
        Mockito.when(frontendConfigProperties.getTicketingSystemUrl()).thenReturn(jiraUrl);
        Mockito.when(errorGroupConfigProperties.isIssueTrackingEnabled()).thenReturn(issueTrackingEnabled);
        Mockito.when(errorGroupConfigProperties.getDefaultSortField()).thenReturn("errorCount");
        Mockito.when(errorGroupConfigProperties.getDefaultSortOrder()).thenReturn("ASC");
        mockMvc.perform(MockMvcRequestBuilders.get("/api/configuration/error-group"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.ticketingSystemUrl").value(jiraUrl))
                .andExpect(MockMvcResultMatchers.jsonPath("$.issueTrackingEnabled").value(issueTrackingEnabled))
                .andExpect(MockMvcResultMatchers.jsonPath("$.defaultSortField").value("errorCount"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.defaultSortOrder").value("ASC"));
    }

    @Test
    void getErrorListConfiguration() throws Exception {
        Mockito.when(errorListConfigProperties.isDefaultNoTicketFilter()).thenReturn(true);
        Mockito.when(errorListConfigProperties.getDefaultStateFilter()).thenReturn("DELETED");

        mockMvc.perform(MockMvcRequestBuilders.get("/api/configuration/error-list"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.defaultNoTicketFilter").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$.defaultStateFilter").value("DELETED"));
    }

    @Test
    void getAuthConfiguration() throws Exception {
        String applicationUrl = "https://example.com";
        String pamsEnvironment = "dev";
        String logoutRedirectUri = "/logout";
        boolean mockPams = false;
        String tokenAwarePattern = "xy/api/*";
        String clientId = "myClientId";
        Boolean autoLogin = true;
        String redirectUrl = "/jeap-frontend/redirect";
        Mockito.when(frontendConfigProperties.getApplicationUrl()).thenReturn(applicationUrl);
        Mockito.when(frontendConfigProperties.getPamsEnabled()).thenReturn(true);
        Mockito.when(frontendConfigProperties.getEffectivePamsEnvironment()).thenReturn(pamsEnvironment);
        Mockito.when(frontendConfigProperties.getLogoutRedirectUri()).thenReturn(logoutRedirectUri);
        Mockito.when(frontendConfigProperties.isMockPamsEffective()).thenReturn(mockPams);
        Mockito.when(frontendConfigProperties.getTokenAwarePattern()).thenReturn(List.of(tokenAwarePattern));
        Mockito.when(frontendConfigProperties.getClientId()).thenReturn(clientId);
        Mockito.when(frontendConfigProperties.getAutoLogin()).thenReturn(autoLogin);
        Mockito.when(frontendConfigProperties.getRedirectUrl()).thenReturn(redirectUrl);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/configuration"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.pamsEnabled").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$.mockPams").value(mockPams))
                .andExpect(MockMvcResultMatchers.jsonPath("$.pamsEnvironment").value(pamsEnvironment))
                .andExpect(MockMvcResultMatchers.jsonPath("$.logoutRedirectUri").value(logoutRedirectUri))
                .andExpect(MockMvcResultMatchers.jsonPath("$.tokenAwarePatterns").value(tokenAwarePattern))
                .andExpect(MockMvcResultMatchers.jsonPath("$.appVersion").isNotEmpty())
                .andExpect(MockMvcResultMatchers.jsonPath("$.authority").value("http://localhost:8080/test"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.clientId").value(clientId))
                .andExpect(MockMvcResultMatchers.jsonPath("$.useAutoLogin").value(autoLogin))
                .andExpect(MockMvcResultMatchers.jsonPath("$.redirectUrl").value(redirectUrl))
                ;
    }

    @Test
    void getAuthConfiguration_pamsDisabled_servesNoEnvironmentAndMocksPams() throws Exception {
        Mockito.when(frontendConfigProperties.getPamsEnabled()).thenReturn(false);
        Mockito.when(frontendConfigProperties.getEffectivePamsEnvironment()).thenReturn(null);
        Mockito.when(frontendConfigProperties.isMockPamsEffective()).thenReturn(true);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/configuration"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.pamsEnabled").value(false))
                .andExpect(MockMvcResultMatchers.jsonPath("$.mockPams").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$.pamsEnvironment").doesNotExist());
    }

    @Profile(PROFILE) // prevent other tests using class path scanning picking up this configuration
    @Configuration
    @Import(ConfigurationController.class)
    static class TestConfiguration {
    }
}
