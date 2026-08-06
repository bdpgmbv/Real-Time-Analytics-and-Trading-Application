package vyshaliprabananthlal.sim.edgar;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public final class EdgarClient {

  public static final String SEC_DATA = "https://data.sec.gov";

  private static final String USER_AGENT =
      System.getenv().getOrDefault("SEC_USER_AGENT", "rtat-learning vyshalibdp@gmail.com");

  private final HttpClient http;
  private final Path outputDir;
  private final String baseUrl;

  public EdgarClient(Path outputDir) {
    this(outputDir, SEC_DATA);
  }

  public EdgarClient(Path outputDir, String baseUrl) {
    this.outputDir = outputDir;
    this.baseUrl = baseUrl;
    this.http =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
  }

  public Path fetchSubmissions(String cik) throws IOException, InterruptedException {
    String padded = padCik(cik);
    URI uri = URI.create(baseUrl + "/submissions/CIK" + padded + ".json");

    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .header("User-Agent", USER_AGENT)
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();

    HttpResponse<String> response =
        http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

    if (response.statusCode() != 200) {
      throw new IOException("EDGAR returned " + response.statusCode() + " for " + uri);
    }

    String body = response.body();
    requireJson(body, uri);

    Files.createDirectories(outputDir);
    Path target = outputDir.resolve("CIK" + padded + ".json");
    Files.writeString(target, body, StandardCharsets.UTF_8);
    return target;
  }

  static void requireJson(String body, URI uri) throws IOException {
    String trimmed = body.stripLeading();
    if (trimmed.isEmpty() || trimmed.charAt(0) != '{') {
      String preview = body.substring(0, Math.min(60, body.length()));
      throw new IOException("Expected JSON from " + uri + " but got: " + preview);
    }
  }

  static String padCik(String cik) {
    String digits = cik.replaceAll("\\D", "");
    return "0".repeat(Math.max(0, 10 - digits.length())) + digits;
  }
}
