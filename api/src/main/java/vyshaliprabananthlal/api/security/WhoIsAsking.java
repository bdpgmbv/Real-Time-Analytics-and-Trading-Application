package vyshaliprabananthlal.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import vyshaliprabananthlal.api.who.NotAllowed;

@Component
public class WhoIsAsking {

  private final String whichClaim;

  public WhoIsAsking(@Value("${rtat.oidc.user-claim:preferred_username}") String whichClaim) {
    this.whichClaim = whichClaim;
  }

  public String userId(Authentication token) {
    if (token == null || !(token.getPrincipal() instanceof Jwt jwt)) {
      throw new NotAllowed("no token was presented");
    }

    String userId = jwt.getClaimAsString(whichClaim);

    if (userId == null || userId.isBlank()) {
      throw new NotAllowed("the token has no " + whichClaim + " claim");
    }
    return userId;
  }
}
