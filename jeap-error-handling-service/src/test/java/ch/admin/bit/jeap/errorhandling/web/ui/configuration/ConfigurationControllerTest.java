package ch.admin.bit.jeap.errorhandling.web.ui.configuration;

import ch.admin.bit.jeap.security.test.resource.configuration.ServletJeapAuthorizationConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.Arrays;

@WebMvcTest(ConfigurationController.class)
@ActiveProfiles("error-controller-test")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ConfigurationControllerTest.TestConfiguration.class)
class ConfigurationControllerTest {

    private static final String PROFILE = "error-controller-test";
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private LogDeepLinkProperties logDeepLinkProperties;
    @MockitoBean
    private FrontendConfigProperties frontendConfigProperties;

    @Test
    void getLogDeepLink() throws Exception {
        String expectedTemplate = "https://log-system.example.com/en/app/myapp/search?q=error";
        Mockito.when(logDeepLinkProperties.getBaseUrl()).thenReturn(expectedTemplate);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/configuration/log-deeplink"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(expectedTemplate));
    }

    @Test
    void getTicketNumberLink() throws Exception {
        String jiraUrl = "https://someJiraUrl/browse/JIRA-007";
        Mockito.when(frontendConfigProperties.getTicketingSystemUrl()).thenReturn(jiraUrl);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/configuration/ticket-number"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.ticketingSystemUrl").value(jiraUrl));
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
        Mockito.when(frontendConfigProperties.getPamsEnvironment()).thenReturn(pamsEnvironment);
        Mockito.when(frontendConfigProperties.getLogoutRedirectUri()).thenReturn(logoutRedirectUri);
        Mockito.when(frontendConfigProperties.getMockPams()).thenReturn(mockPams);
        Mockito.when(frontendConfigProperties.getTokenAwarePattern()).thenReturn(Arrays.asList(tokenAwarePattern));
        Mockito.when(frontendConfigProperties.getClientId()).thenReturn(clientId);
        Mockito.when(frontendConfigProperties.getAutoLogin()).thenReturn(autoLogin);
        Mockito.when(frontendConfigProperties.getRedirectUrl()).thenReturn(redirectUrl);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/configuration"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.pamsEnvironment").value(pamsEnvironment))
                .andExpect(MockMvcResultMatchers.jsonPath("$.logoutRedirectUri").value(logoutRedirectUri))
                .andExpect(MockMvcResultMatchers.jsonPath("$.tokenAwarePatterns").value(tokenAwarePattern))
                .andExpect(MockMvcResultMatchers.jsonPath("$.appVersion").value("??"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.authority").value("http://localhost:8080/test"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.clientId").value(clientId))
                .andExpect(MockMvcResultMatchers.jsonPath("$.useAutoLogin").value(autoLogin))
                .andExpect(MockMvcResultMatchers.jsonPath("$.redirectUrl").value(redirectUrl))
                ;
    }

    @Profile(PROFILE) // prevent other tests using class path scanning picking up this configuration
    @Configuration
    @ComponentScan
    static class TestConfiguration extends ServletJeapAuthorizationConfig {

        // You have to provide the system name and the application context to the test support base class.
        TestConfiguration(ApplicationContext applicationContext) {
            super("jme", applicationContext);
        }
    }
}

