package ch.admin.bit.jeap.errorhandling.domain.user;

import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.User;
import ch.admin.bit.jeap.security.resource.semanticAuthentication.ServletSemanticAuthorization;
import ch.admin.bit.jeap.security.resource.token.JeapAuthenticationContext;
import ch.admin.bit.jeap.security.resource.token.JeapAuthenticationToken;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private static final JeapAuthenticationContext CTX_USER = JeapAuthenticationContext.USER;
    private static final String CTX_USER_VALUE = JeapAuthenticationContext.USER.name();
    private static final JeapAuthenticationContext CTX_SYS = JeapAuthenticationContext.SYS;
    private static final String CTX_SYS_VALUE = JeapAuthenticationContext.SYS.name();
    private static final String SUBJECT_VALUE = "test-subject";
    private static final String EXT_ID_CLAIM_NAME = "ext_id";
    private static final String FAMILY_NAME_CLAIM_NAME = "family_name";
    private static final String GIVEN_NAME_CLAIM_NAME = "given_name";
    private static final String EXT_ID_VALUE = "test-ext-id";
    private static final String FAMILY_NAME_VALUE = "test-family-name";
    private static final String GIVEN_NAME_VALUE = "test-given-name";


    @Test
    void testGetAuthenticatedUser_InUserContext() {
        UserService userService = new UserService(createServletSemanticAuthorizationMock(createTestTokenInUserContext()));

        Optional<User> userOptional = userService.getAuthenticatedUser();

        assertThat(userOptional).isPresent();
        User user = userOptional.get();
        assertThat(user.getAuthContext()).isEqualTo(CTX_USER_VALUE);
        assertThat(user.getSubject()).isEqualTo(SUBJECT_VALUE);
        assertThat(user.getExtId()).isEqualTo(EXT_ID_VALUE);
        assertThat(user.getFamilyName()).isEqualTo(FAMILY_NAME_VALUE);
        assertThat(user.getGivenName()).isEqualTo(GIVEN_NAME_VALUE);
    }

    @Test
    void testGetAuthenticatedUser_InSystemContext() {
        UserService userService = new UserService(createServletSemanticAuthorizationMock(createTestTokenInSystemContext()));

        Optional<User> userOptional = userService.getAuthenticatedUser();

        assertThat(userOptional).isPresent();
        User user = userOptional.get();
        assertThat(user.getAuthContext()).isEqualTo(CTX_SYS_VALUE);
        assertThat(user.getSubject()).isEqualTo(SUBJECT_VALUE);
        assertThat(user.getExtId()).isNull();
        assertThat(user.getFamilyName()).isNull();
        assertThat(user.getGivenName()).isNull();
    }

    @Test
    void testGetAuthenticatedUser_Unauthenticated() {
        UserService userService = new UserService(createServletSemanticAuthorizationMock(null));

        Optional<User> userOptional = userService.getAuthenticatedUser();

        assertThat(userOptional).isNotPresent();
    }

    @Test
    void testGetAuthenticatedUser_ExceptionOnGetAuthentication() {
        ServletSemanticAuthorization servletSemanticAuthorizationMock =  mock(ServletSemanticAuthorization.class);
        when(servletSemanticAuthorizationMock.getAuthenticationToken()).thenThrow(new RuntimeException());
        UserService userService = new UserService(servletSemanticAuthorizationMock);

        Optional<User> userOptional = userService.getAuthenticatedUser();

        assertThat(userOptional).isNotPresent();
    }

    private ServletSemanticAuthorization createServletSemanticAuthorizationMock(Jwt jwt) {
        ServletSemanticAuthorization servletSemanticAuthorizationMock =  mock(ServletSemanticAuthorization.class);
        if (jwt != null) {
            JeapAuthenticationToken jeapAuthenticationToken = new JeapAuthenticationToken(jwt, Set.of(), Map.of(), Set.of());
            when(servletSemanticAuthorizationMock.getAuthenticationToken()).thenReturn(jeapAuthenticationToken);
        } else {
            when(servletSemanticAuthorizationMock.getAuthenticationToken()).thenReturn(null);
        }
        return servletSemanticAuthorizationMock;
    }

    private Jwt createTestTokenInUserContext() {
        return Jwt.withTokenValue("dummy-user-token-value")
                .header("dummy-header-name", "dummy-header-value")
                .subject(SUBJECT_VALUE)
                .claim(JeapAuthenticationContext.getContextJwtClaimName(), CTX_USER)
                .claim(EXT_ID_CLAIM_NAME, EXT_ID_VALUE)
                .claim(FAMILY_NAME_CLAIM_NAME, FAMILY_NAME_VALUE)
                .claim(GIVEN_NAME_CLAIM_NAME, GIVEN_NAME_VALUE)
                .build();
    }

    private Jwt createTestTokenInSystemContext() {
        return Jwt.withTokenValue("dummy-system-token-value")
                .header("dummy-header-name", "dummy-header-value")
                .subject(SUBJECT_VALUE)
                .claim(JeapAuthenticationContext.getContextJwtClaimName(), CTX_SYS)
                .build();
    }

}
