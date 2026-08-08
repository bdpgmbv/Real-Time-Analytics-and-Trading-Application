package vyshaliprabananthlal.gateway.limit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RateLimitKeyResolver implements KeyResolver {

  static final String NOBODY_IN_PARTICULAR = "anonymous";

  @Override
  public Mono<String> resolve(ServerWebExchange exchange) {
    return exchange
        .getPrincipal()
        .map(RateLimitKeyResolver::nameOf)
        .defaultIfEmpty(callerAddress(exchange));
  }

  static String nameOf(java.security.Principal principal) {
    String name = principal.getName();

    return name == null || name.isBlank() ? NOBODY_IN_PARTICULAR : name;
  }

  static String callerAddress(ServerWebExchange exchange) {
    var remote = exchange.getRequest().getRemoteAddress();

    return remote == null ? NOBODY_IN_PARTICULAR : remote.getAddress().getHostAddress();
  }
}
