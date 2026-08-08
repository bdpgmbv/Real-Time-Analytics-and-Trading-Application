package vyshaliprabananthlal.stream.message;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MovingPriceTest {

  @Test
  @DisplayName("the message carries the fields the price receiver reads")
  void theMessageCarriesWhatTheReceiverReads() {
    MovingPrice price = new MovingPrice(100101, 189.32);

    String message = price.asMessage();

    assertThat(message).contains("\"productId\":100101");
    assertThat(message).contains("\"price\":189.32");
    assertThat(message).contains("\"howFresh\":\"DELAYED 20 MINUTES\"");
  }

  @Test
  @DisplayName("a price moves when told to, but stays near where it started")
  void aPriceStaysNearWhereItStarted() {
    MovingPrice price = new MovingPrice(1, 100.0);

    for (int tick = 0; tick < 5000; tick++) {
      price.move();
    }

    assertThat(price.currentPrice()).isBetween(80.0, 120.0);
  }

  @Test
  @DisplayName("a price never goes to zero or below, however long it drifts")
  void aPriceNeverGoesToZero() {
    MovingPrice penny = new MovingPrice(1, 0.02);

    for (int tick = 0; tick < 20000; tick++) {
      penny.move();
    }

    assertThat(penny.currentPrice()).isGreaterThan(0.0);
  }

  @Test
  @DisplayName("the message is keyed on the product so one product stays on one partition")
  void theKeyIsTheProduct() {
    assertThat(new MovingPrice(100101, 1.0).messageKey()).isEqualTo("100101");
  }

  @Test
  @DisplayName("the price is cut to six decimals, which is what the column holds")
  void thePriceIsCutToSixDecimals() {
    MovingPrice price = new MovingPrice(1, 1.23456789);

    assertThat(price.asMessage()).contains("\"price\":1.234568");
  }
}
