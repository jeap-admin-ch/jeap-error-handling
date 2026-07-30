package ch.admin.bit.jeap.errorhandling.web.ui.configuration;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates the PAMS related configuration properties and the values derived from them. The properties are
 * validated directly instead of by starting an application context, as the Spring test context cache is
 * limited to a single context in this module.
 */
class FrontendConfigPropertiesTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void pamsIsEnabledByDefault() {
        assertThat(new FrontendConfigProperties().getPamsEnabled()).isTrue();
        assertThat(new FrontendConfigProperties().getMockPams()).isFalse();
    }

    @Test
    void pamsEnvironmentIsRequiredWhenPamsIsEnabled() {
        FrontendConfigProperties properties = validProperties();
        properties.setPamsEnvironment(null);

        assertThat(violationPropertyPaths(properties)).contains("pamsEnvironmentSetWhenPamsEnabled");
    }

    @Test
    void pamsEnvironmentIsNotRequiredWhenPamsIsDisabled() {
        FrontendConfigProperties properties = validProperties();
        properties.setPamsEnabled(false);
        properties.setPamsEnvironment(null);

        assertThat(violationPropertyPaths(properties)).isEmpty();
    }

    @Test
    void mockPamsIsNotRequiredWhenPamsIsDisabled() {
        FrontendConfigProperties properties = validProperties();
        properties.setPamsEnabled(false);
        properties.setPamsEnvironment(null);

        assertThat(properties.isMockPamsEffective()).isTrue();
    }

    @Test
    void disablingPamsOverridesAnExplicitlyDisabledPamsMock() {
        FrontendConfigProperties properties = validProperties();
        properties.setPamsEnabled(false);
        properties.setMockPams(false);

        assertThat(properties.isMockPamsEffective()).isTrue();
    }

    @Test
    void pamsMockIsNotEnabledByEnablingPams() {
        FrontendConfigProperties properties = validProperties();

        assertThat(properties.isMockPamsEffective()).isFalse();

        properties.setMockPams(true);
        assertThat(properties.isMockPamsEffective()).isTrue();
    }

    @Test
    void pamsEnvironmentIsNotServedWhenPamsIsDisabled() {
        FrontendConfigProperties properties = validProperties();
        assertThat(properties.getEffectivePamsEnvironment()).isEqualTo("REF");

        properties.setPamsEnabled(false);
        assertThat(properties.getEffectivePamsEnvironment()).isNull();
    }

    private Set<String> violationPropertyPaths(FrontendConfigProperties properties) {
        return validator.validate(properties).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private FrontendConfigProperties validProperties() {
        FrontendConfigProperties properties = new FrontendConfigProperties();
        properties.setStsServer("http://localhost/auth");
        properties.setApplicationUrl("http://localhost:8080");
        properties.setLogoutRedirectUri("/logout");
        properties.setPamsEnvironment("REF");
        properties.setClientId("client-id");
        properties.setSilentRenew(true);
        properties.setSystemName("jme");
        properties.setAutoLogin(true);
        properties.setRenewUserInfoAfterTokenRenew(true);
        properties.setRedirectUrl("/redirect");
        properties.setTicketingSystemUrl("http://localhost/tickets");
        return properties;
    }
}
