package vyshaliprabananthlal.gateway.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class ClientKeyResolver implements KeyResolver {

  public static final String CLIENT_CLAIM = "client_id";

  private static final String ANONYMOUS_KEY = "anonymous";

  @Override
  public Mono<String> resolve(ServerWebExchange exchange) {
    return ReactiveSecurityContextHolder.getContext()
        .map(context -> context.getAuthentication())
        .filter(JwtAuthenticationToken.class::isInstance)
        .cast(JwtAuthenticationToken.class)
        .map(JwtAuthenticationToken::getToken)
        .map(ClientKeyResolver::keyFor)
        .defaultIfEmpty(ANONYMOUS_KEY);
  }

  private static String keyFor(Jwt token) {
    String clientId = token.getClaimAsString(CLIENT_CLAIM);
    if (clientId != null && !clientId.isBlank()) {
      return "client:" + clientId;
    }
    String subject = token.getSubject();
    if (subject != null && !subject.isBlank()) {
      return "sub:" + subject;
    }
    return ANONYMOUS_KEY;
  }
}
