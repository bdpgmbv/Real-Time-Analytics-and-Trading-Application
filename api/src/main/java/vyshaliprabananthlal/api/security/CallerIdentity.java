package vyshaliprabananthlal.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import vyshaliprabananthlal.api.who.Entitlements;

@Component
public class CallerIdentity {

    private final String whichClaim;

    public CallerIdentity(@Value("${rtat.oidc.user-claim:preferred_username}") String whichClaim) {
        this.whichClaim = whichClaim;
    }

    public String userId(Authentication token) {
        if (token == null || !(token.getPrincipal() instanceof Jwt jwt)) {
            throw new Entitlements.NotAllowed("no token was presented");
        }

        String userId = jwt.getClaimAsString(whichClaim);

        if (userId == null || userId.isBlank()) {
            throw new Entitlements.NotAllowed("the token has no " + whichClaim + " claim");
        }
        return userId;
    }
}
