package ch.admin.bit.jeap.errorhandling.domain.user;

import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.User;
import ch.admin.bit.jeap.security.resource.semanticAuthentication.ServletSemanticAuthorization;
import ch.admin.bit.jeap.security.resource.token.JeapAuthenticationContext;
import ch.admin.bit.jeap.security.resource.token.JeapAuthenticationToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final ServletSemanticAuthorization jeapSemanticAuthorization;

    public Optional<User> getAuthenticatedUser() {
        return getJeapAuthenticationToken().map(UserService::createUser);
    }

    private Optional<JeapAuthenticationToken> getJeapAuthenticationToken() {
        try {
            return Optional.ofNullable(jeapSemanticAuthorization.getAuthenticationToken());
        } catch (Exception e) {
            log.debug("Unable to get JeapAuthenticationToken for user.", e);
            return Optional.empty();
        }
    }

    private static User createUser(JeapAuthenticationToken jeapAuthenticationToken) {
        JeapAuthenticationContext context = jeapAuthenticationToken.getJeapAuthenticationContext();
        if (JeapAuthenticationContext.USER == context) {
            return User.builder()
                    .authContext(context.name())
                    .subject(jeapAuthenticationToken.getTokenSubject())
                    .extId(jeapAuthenticationToken.getTokenExtId())
                    .givenName(jeapAuthenticationToken.getTokenGivenName())
                    .familyName(jeapAuthenticationToken.getTokenFamilyName())
                    .build();
        } else {
            return User.builder()
                    .authContext(context.name())
                    .subject(jeapAuthenticationToken.getTokenSubject())
                    .build();
        }
    }

}
