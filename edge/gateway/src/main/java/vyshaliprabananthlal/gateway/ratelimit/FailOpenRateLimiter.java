package vyshaliprabananthlal.gateway.ratelimit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Primary
@Component("failOpenRateLimiter")
public class FailOpenRateLimiter implements RateLimiter<RedisRateLimiter.Config> {

  private static final Logger log = LoggerFactory.getLogger(FailOpenRateLimiter.class);

  private final RedisRateLimiter delegate;
  private final Counter failures;

  public FailOpenRateLimiter(RedisRateLimiter delegate, MeterRegistry meters) {
    this.delegate = delegate;
    this.failures =
        Counter.builder("rtat.gateway.ratelimit.unavailable")
            .description("Requests admitted without a quota check because Redis was unreachable")
            .register(meters);
  }

  @Override
  public Mono<Response> isAllowed(String routeId, String id) {
    return delegate
        .isAllowed(routeId, id)
        .onErrorResume(
            error -> {
              failures.increment();
              log.warn(
                  "Rate limiter unavailable for route {}; admitting request unchecked",
                  routeId,
                  error);
              return Mono.just(new Response(true, Map.of()));
            });
  }

  @Override
  public Map<String, RedisRateLimiter.Config> getConfig() {
    return delegate.getConfig();
  }

  @Override
  public Class<RedisRateLimiter.Config> getConfigClass() {
    return delegate.getConfigClass();
  }

  @Override
  public RedisRateLimiter.Config newConfig() {
    return delegate.newConfig();
  }
}
