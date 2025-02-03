package ch.admin.bit.jeap.errorhandling.web.ui;

import ch.admin.bit.jeap.errorhandling.web.ui.configuration.FrontendConfigProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FrontendWebConfigTest {

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
}
