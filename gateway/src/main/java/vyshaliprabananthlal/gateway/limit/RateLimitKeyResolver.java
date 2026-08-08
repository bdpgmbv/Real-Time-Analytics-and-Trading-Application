package vyshaliprabananthlal.gateway.limit;

import java.security.Principal;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Decides whose rate limit bucket a request comes out of.
 *
 * <p>Keyed on who is asking, taken from the token, rather than on the address they came from.
 * Two clients behind one office address get their own allowance, and one client cannot get more
 * by opening more connections. That is the difference between a rate limit and a multi-tenancy
 * control.
 *
 * <p>A caller with no token falls back to their address, so an unauthenticated flood is still
 * bounded.
 */
@Component
public class RateLimitKeyResolver implements KeyResolver {

    static final String ANONYMOUS = "anonymous";

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        return exchange.getPrincipal().map(RateLimitKeyResolver::nameFrom).defaultIfEmpty(callerAddress(exchange));
    }

    static String nameFrom(Principal principal) {
        String name = principal.getName();

        // A blank name must not become an empty key, or every such caller would share one bucket.
        return name == null || name.isBlank() ? ANONYMOUS : name;
    }

    static String callerAddress(ServerWebExchange exchange) {
        var remote = exchange.getRequest().getRemoteAddress();

        return remote == null ? ANONYMOUS : remote.getAddress().getHostAddress();
    }
}
