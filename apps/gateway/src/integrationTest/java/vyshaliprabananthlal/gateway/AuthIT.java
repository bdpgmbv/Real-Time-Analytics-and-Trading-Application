package vyshaliprabananthlal.gateway;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Import(GatewayTestSupport.TestBeans.class)
class AuthIT {

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    GatewayTestSupport.redisProperties(registry);
  }

  @LocalServerPort private int port;

  @LocalManagementPort private int managementPort;

  private WebTestClient client;

  @BeforeEach
  void bindClient() {
    client = clientFor(port);
  }

  private static WebTestClient clientFor(int boundPort) {
    return WebTestClient.bindToServer()
        .baseUrl("http://localhost:" + boundPort)
        .responseTimeout(Duration.ofSeconds(10))
        .build();
  }

  @Test
  @DisplayName("an unauthenticated request never reaches a downstream route")
  void rejectsMissingToken() {
    client.get().uri("/api/v1/echo").exchange().expectStatus().isUnauthorized();
  }

  @Test
  void rejectsGarbageToken() {
    client
        .get()
        .uri("/api/v1/echo")
        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token")
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void acceptsSignedToken() {
    client
        .get()
        .uri("/api/v1/echo")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + GatewayTestSupport.tokenFor("helikon"))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("ok");
  }

  @Test
  @DisplayName("no actuator data leaks on the traffic port")
  void actuatorIsNotOnTheTrafficPort() {
    client.get().uri("/actuator/health").exchange().expectStatus().isNotFound();
    client.get().uri("/actuator/gateway/routes").exchange().expectStatus().isUnauthorized();
  }

  @Test
  @DisplayName("health is reachable on the management port without a token so probes work")
  void healthIsPublicOnManagementPort() {
    clientFor(managementPort)
        .get()
        .uri("/actuator/health")
        .exchange()
        .expectStatus()
        .is2xxSuccessful();
  }
}
