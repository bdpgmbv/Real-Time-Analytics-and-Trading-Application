package vyshaliprabananthlal.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Import(GatewayTestSupport.TestBeans.class)
class RateLimitIT {

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    GatewayTestSupport.redisProperties(registry);
  }

  @LocalServerPort private int port;

  @Autowired private ReactiveStringRedisTemplate redis;

  private WebTestClient client;

  @BeforeEach
  void bindClientAndClearBuckets() {
    client =
        WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .responseTimeout(java.time.Duration.ofSeconds(10))
            .build();
    GatewayTestSupport.flushRateLimitState(redis);
  }

  private int callAs(String clientId) {
    return client
        .get()
        .uri("/api/v1/echo")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + GatewayTestSupport.tokenFor(clientId))
        .exchange()
        .returnResult(String.class)
        .getStatus()
        .value();
  }

  @Test
  @DisplayName("the limiter actually runs and reports remaining quota")
  void reportsRemainingQuota() {
    client
        .get()
        .uri("/api/v1/echo")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + GatewayTestSupport.tokenFor("headercheck"))
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .exists("X-RateLimit-Remaining");
  }

  @Test
  @DisplayName("a client past its burst gets 429, not a slow 200")
  void refusesTrafficBeyondBurstCapacity() {
    List<Integer> statuses = new ArrayList<>();
    for (int i = 0; i < 15; i++) {
      statuses.add(callAs("helikon"));
    }

    assertThat(statuses).contains(200);
    assertThat(statuses).contains(429);
  }

  @Test
  @DisplayName("one client's burst does not consume another client's quota")
  void bucketsAreIsolatedPerClient() {
    for (int i = 0; i < 15; i++) {
      callAs("noisy");
    }

    assertThat(callAs("quiet")).isEqualTo(200);
  }
}
