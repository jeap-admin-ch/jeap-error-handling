package ch.admin.bit.jeap.errorhandling.web.ui;

import ch.admin.bit.jeap.errorhandling.web.ui.configuration.FrontendConfigProperties;
import ch.admin.bit.jeap.starter.application.web.FrontendRouteRedirectExceptionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@Configuration
@RequiredArgsConstructor
class FrontendWebConfig implements WebMvcConfigurer {

    private final FrontendConfigProperties frontendConfigProperties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String origin = getOrigin();
        registry.addMapping("/**")
                .allowedHeaders("*")
                .allowedMethods("*")
                .allowedOrigins(origin)
                .allowCredentials(false);
    }

    String getOrigin() {
        UriComponents uriComponents = UriComponentsBuilder
                .fromUriString(frontendConfigProperties.getApplicationUrl()).build();
        String origin = "%s://%s".formatted(uriComponents.getScheme(), uriComponents.getHost());
        if (uriComponents.getPort() != -1) {
            origin += ":" + uriComponents.getPort();
        }
        return origin;
    }

    @Bean
    public FrontendRouteRedirectExceptionHandler frontendRouteRedirectExceptionHandler() {
        return new FrontendRouteRedirectExceptionHandler();
    }
}
