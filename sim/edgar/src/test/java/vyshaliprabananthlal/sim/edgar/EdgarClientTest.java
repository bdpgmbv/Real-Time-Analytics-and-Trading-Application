package vyshaliprabananthlal.sim.edgar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EdgarClientTest {

  private static final URI ANY = URI.create("https://data.sec.gov/submissions/CIK0001067983.json");

  @Test
  @DisplayName("CIK is padded to ten digits, as EDGAR requires")
  void padsCikToTenDigits() {
    assertThat(EdgarClient.padCik("1067983")).isEqualTo("0001067983");
    assertThat(EdgarClient.padCik("0001067983")).isEqualTo("0001067983");
    assertThat(EdgarClient.padCik("CIK1067983")).isEqualTo("0001067983");
  }

  @Test
  void acceptsJson() {
    assertThatCode(() -> EdgarClient.requireJson("{\"cik\":\"0001067983\"}", ANY))
        .doesNotThrowAnyException();
    assertThatCode(() -> EdgarClient.requireJson("\n\n  {\"a\":1}", ANY))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("gzip bytes are rejected rather than saved under a .json name")
  void rejectsGzip() {
    String gzipMagic =
        new String(new byte[] {0x1f, (byte) 0x8b, 0x08, 0x00}, StandardCharsets.ISO_8859_1);

    assertThatThrownBy(() -> EdgarClient.requireJson(gzipMagic, ANY))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Expected JSON");
  }

  @Test
  @DisplayName("an HTML error page is rejected, not written out as data")
  void rejectsHtmlErrorPage() {
    assertThatThrownBy(
            () -> EdgarClient.requireJson("<html><body>403 Forbidden</body></html>", ANY))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("403 Forbidden");
  }

  @Test
  void rejectsEmptyBody() {
    assertThatThrownBy(() -> EdgarClient.requireJson("   ", ANY)).isInstanceOf(IOException.class);
  }
}
