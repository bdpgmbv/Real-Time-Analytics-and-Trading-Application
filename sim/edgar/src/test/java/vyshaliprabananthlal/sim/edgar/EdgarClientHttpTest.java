package vyshaliprabananthlal.sim.edgar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EdgarClientHttpTest {

  @TempDir Path outputDir;

  private HttpServer server;
  private String baseUrl;
  private final AtomicReference<String> lastUserAgent = new AtomicReference<>();

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    baseUrl = "http://localhost:" + server.getAddress().getPort();
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  private void respond(int status, byte[] body) {
    server.createContext(
        "/submissions/",
        exchange -> {
          lastUserAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
          exchange.sendResponseHeaders(status, body.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
          }
        });
  }

  @Test
  @DisplayName("saves the body when the server returns JSON")
  void savesJson() throws Exception {
    respond(
        200, "{\"cik\":\"0001067983\",\"name\":\"TEST FUND\"}".getBytes(StandardCharsets.UTF_8));

    Path saved = new EdgarClient(outputDir, baseUrl).fetchSubmissions("1067983");

    assertThat(saved).exists().hasFileName("CIK0001067983.json");
    assertThat(Files.readString(saved)).contains("TEST FUND");
  }

  @Test
  @DisplayName("identifies itself, as SEC policy requires")
  void sendsUserAgent() throws Exception {
    respond(200, "{}".getBytes(StandardCharsets.UTF_8));

    new EdgarClient(outputDir, baseUrl).fetchSubmissions("1067983");

    assertThat(lastUserAgent.get()).isNotNull().doesNotContain("Java-http-client");
  }

  @Test
  @DisplayName("a 403 fails loudly instead of writing an empty file")
  void failsOnForbidden() {
    respond(403, "Forbidden".getBytes(StandardCharsets.UTF_8));

    EdgarClient client = new EdgarClient(outputDir, baseUrl);

    assertThatThrownBy(() -> client.fetchSubmissions("1067983"))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("403");

    assertThat(outputDir).isEmptyDirectory();
  }

  @Test
  @DisplayName("gzip bytes are refused rather than saved under a .json name")
  void failsOnGzipBody() throws Exception {
    var buffer = new java.io.ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(buffer)) {
      gzip.write("{\"cik\":\"0001067983\"}".getBytes(StandardCharsets.UTF_8));
    }
    respond(200, buffer.toByteArray());

    EdgarClient client = new EdgarClient(outputDir, baseUrl);

    assertThatThrownBy(() -> client.fetchSubmissions("1067983"))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Expected JSON");

    assertThat(outputDir).isEmptyDirectory();
  }
}
