package ch.admin.bit.jeap.errorhandling.web.ui;

import ch.admin.bit.jeap.errorhandling.web.ui.configuration.FrontendConfigProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@ActiveProfiles("frontend-web-config-test")
@ContextConfiguration(classes = FrontendWebConfigTest.TestConfiguration.class)
@TestPropertySource(properties = {
        "jeap.errorhandling.frontend.application-url=http://localhost:4200",
        "jeap.errorhandling.frontend.sts-server=http://localhost",
        "jeap.errorhandling.frontend.logout-redirect-uri=/logout",
        "jeap.errorhandling.frontend.mock-pams=false",
        "jeap.errorhandling.frontend.pams-environment=dev",
        "jeap.errorhandling.frontend.client-id=test-client",
        "jeap.errorhandling.frontend.silent-renew=false",
        "jeap.errorhandling.frontend.system-name=test-system",
        "jeap.errorhandling.frontend.auto-login=false",
        "jeap.errorhandling.frontend.renew-user-info-after-token-renew=false",
        "jeap.errorhandling.frontend.redirect-url=/redirect",
        "jeap.errorhandling.frontend.ticketing-system-url=http://localhost/tickets"
})
class FrontendWebConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rootForwardsToIndexHtml() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void getOrigin_local() {
        FrontendConfigProperties props = new FrontendConfigProperties();
        props.setApplicationUrl("http://localhost:4200");
        FrontendWebConfig config = new FrontendWebConfig(props);

        assertThat(config.getOrigin())
                .isEqualTo("http://localhost:4200");
    }

    @Test
    void getOrigin_withContext() {
        FrontendConfigProperties props = new FrontendConfigProperties();
        props.setApplicationUrl("https://some-host/error-handling/");
        FrontendWebConfig config = new FrontendWebConfig(props);

        assertThat(config.getOrigin())
                .isEqualTo("https://some-host");
    }

    @Profile("frontend-web-config-test")
    @Configuration
    @Import({FrontendWebConfig.class, FrontendConfigProperties.class})
    static class TestConfiguration {
    }
}
