package ch.admin.bit.jeap.errorhandling.web.ui.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * The Configuration service needs to be public as its used to determine the actual login service.
 */
@Configuration
class ConfigurationControllerSecurityConfig {
    private static RequestMatcher configurationServiceMatcher() {
        return PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/configuration/**");
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 12)
    SecurityFilterChain configSecurityFilterChain(HttpSecurity http) {
        http.securityMatcher(configurationServiceMatcher());
        http.authorizeHttpRequests( authorizeHttpRequests ->
                authorizeHttpRequests.anyRequest().permitAll());
        return http.build();
    }
}
