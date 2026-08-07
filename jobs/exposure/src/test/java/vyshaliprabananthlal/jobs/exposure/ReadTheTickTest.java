package vyshaliprabananthlal.jobs.exposure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReadTheTickTest {

  private final ReadTheTick reading = new ReadTheTick();

  @Test
  @DisplayName("a price message the sender writes is read back the same")
  void aRealMessageIsReadBack() throws Exception {
    PriceTick tick =
        reading.map("{\"productId\":100101,\"price\":189.32,\"howFresh\":\"DELAYED 20 MINUTES\"}");

    assertThat(tick.productId()).isEqualTo(100101);
    assertThat(tick.price()).isEqualTo(189.32);
  }

  @Test
  @DisplayName("fields we do not use are ignored rather than refused")
  void extraFieldsAreIgnored() throws Exception {
    PriceTick tick = reading.map("{\"productId\":1,\"price\":2.5,\"somethingNew\":\"whatever\"}");

    assertThat(tick.price()).isEqualTo(2.5);
  }

  @Test
  @DisplayName("a message that is not JSON stops the job rather than being read as zero")
  void rubbishIsRefused() {
    assertThatThrownBy(() -> reading.map("this is not json")).isInstanceOf(Exception.class);
  }

  @Test
  @DisplayName("the same instance is reused across messages, as Flink will reuse it")
  void theSameInstanceIsReused() throws Exception {
    assertThat(reading.map("{\"productId\":1,\"price\":10}").price()).isEqualTo(10.0);
    assertThat(reading.map("{\"productId\":2,\"price\":20}").price()).isEqualTo(20.0);
  }
}
