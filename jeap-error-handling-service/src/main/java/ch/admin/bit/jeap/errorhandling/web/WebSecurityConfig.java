package ch.admin.bit.jeap.errorhandling.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;


@Configuration
public class WebSecurityConfig  {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 11)
    public SecurityFilterChain uiSecurityFilterChain(HttpSecurity http) throws Exception {
        // csrf is unnecessary as we're already using an oauth token
        http.csrf(csrf -> csrf.disable());

        // protect the API
        // allow public access to frontend resources (i.e. non-/api-routes)
        // permit open access to open API docs & swagger ui as they are only enabled on test environments
        RequestMatcher antPathMatcher = new AntPathRequestMatcher("/api/**");
        RequestMatcher matcher = new NegatedRequestMatcher(antPathMatcher);
        http.securityMatcher(matcher)
                .authorizeHttpRequests(authorizeHttpRequests ->
                        authorizeHttpRequests.anyRequest().permitAll());

        // this is used for the auth - silent-renew.html
        http.headers(headers ->
                headers.frameOptions( frameOptions ->
                        frameOptions.sameOrigin()));

        return http.build();
    }

}
