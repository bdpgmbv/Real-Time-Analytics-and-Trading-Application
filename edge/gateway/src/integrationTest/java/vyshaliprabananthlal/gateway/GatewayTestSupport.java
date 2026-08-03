package vyshaliprabananthlal.gateway;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.GenericContainer;
import reactor.core.publisher.Mono;

public final class GatewayTestSupport {

  private static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7-alpine")
          .withExposedPorts(6379)
          .withStartupTimeout(Duration.ofMinutes(5));

  private static final RSAKey SIGNING_KEY = generateKey();

  static {
    REDIS.start();
  }

  private GatewayTestSupport() {}

  private static RSAKey generateKey() {
    try {
      return new RSAKeyGenerator(2048).keyID("it").generate();
    } catch (Exception e) {
      throw new IllegalStateException("Could not generate an RSA key for tests", e);
    }
  }

  public static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }

  public static String tokenFor(String clientId) {
    try {
      JWTClaimsSet claims =
          new JWTClaimsSet.Builder()
              .subject("user-" + clientId)
              .claim("client_id", clientId)
              .issueTime(Date.from(Instant.now()))
              .expirationTime(Date.from(Instant.now().plus(Duration.ofMinutes(10))))
              .build();

      SignedJWT jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(SIGNING_KEY.getKeyID()).build(),
              claims);
      jwt.sign(new RSASSASigner(SIGNING_KEY));
      return jwt.serialize();
    } catch (Exception e) {
      throw new IllegalStateException("Could not mint a test token", e);
    }
  }

  public static void flushRateLimitState(ReactiveStringRedisTemplate redis) {
    redis.getConnectionFactory().getReactiveConnection().serverCommands().flushDb().block();
  }

  @TestConfiguration
  public static class TestBeans {

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() throws Exception {
      return NimbusReactiveJwtDecoder.withPublicKey(SIGNING_KEY.toRSAPublicKey()).build();
    }
  }

  @RestController
  public static class EchoController {

    @GetMapping(value = "/internal/echo", produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<String> echo() {
      return Mono.just("ok");
    }
  }
}
