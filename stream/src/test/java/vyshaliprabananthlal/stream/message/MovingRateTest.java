package vyshaliprabananthlal.stream.message;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MovingRateTest {

  @Test
  @DisplayName("the message carries the fields the rate receiver reads")
  void theMessageCarriesWhatTheReceiverReads() {
    MovingRate rate = new MovingRate("EUR", "USD", 1.1542);

    String message = rate.asMessage();

    assertThat(message).contains("\"from\":\"EUR\"");
    assertThat(message).contains("\"to\":\"USD\"");
    assertThat(message).contains("\"rate\":1.1542");
  }

  @Test
  @DisplayName("a currency against itself stays at exactly one, however long it runs")
  void aCurrencyAgainstItselfNeverMoves() {
    MovingRate sameBothSides = new MovingRate("USD", "USD", 1.0);

    for (int tick = 0; tick < 10000; tick++) {
      sameBothSides.move();
    }

    assertThat(sameBothSides.currentPrice()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("a rate drifts by small amounts and is pulled back towards where it started")
  void aRateIsPulledBackTowardsWhereItStarted() {
    MovingRate rate = new MovingRate("JPY", "USD", 0.006336);

    for (int tick = 0; tick < 10000; tick++) {
      rate.move();
    }

    assertThat(rate.currentPrice()).isBetween(0.006, 0.0067);
  }

  @Test
  @DisplayName("the message is keyed on the pair so one pair stays on one partition")
  void theKeyIsThePair() {
    assertThat(new MovingRate("EUR", "USD", 1.0).messageKey()).isEqualTo("EUR-USD");
  }
}
