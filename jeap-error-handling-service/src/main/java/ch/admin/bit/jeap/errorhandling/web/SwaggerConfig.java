package ch.admin.bit.jeap.errorhandling.web;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(
        info = @Info(
                title = "Error Handling SCS",
                description = "Rest API for Error Handling",
                version = "1.0.0"
        ),
        security = {@SecurityRequirement(name = "OIDC Enduser"), @SecurityRequirement(name="OIDC System")}
)
@Configuration
class SwaggerConfig {

    @Bean
    GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public-api")
                .pathsToMatch("/api/**")
                .build();
    }

}
