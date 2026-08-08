package vyshaliprabananthlal.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import vyshaliprabananthlal.api.who.Entitlements;

/**
 * Who is asking, taken from the token and nothing else.
 *
 * <p>Which claim carries the identity is configurable because it depends on how the client
 * provisions users. Keycloak's sub is a UUID we did not choose; preferred_username matches
 * the identifier already in app_user. Where we control provisioning, sub is the better
 * choice because it survives a rename.
 */
@Component
public class CallerIdentity {

    private final String claimName;

    public CallerIdentity(@Value("${rtat.oidc.user-claim:preferred_username}") String claimName) {
        this.claimName = claimName;
    }

    public String userId(Authentication token) {
        if (token == null || !(token.getPrincipal() instanceof Jwt jwt)) {
            throw new Entitlements.NotAllowed("no token was presented");
        }

        String userId = jwt.getClaimAsString(claimName);

        if (userId == null || userId.isBlank()) {
            throw new Entitlements.NotAllowed("the token has no " + claimName + " claim");
        }
        return userId;
    }
}
