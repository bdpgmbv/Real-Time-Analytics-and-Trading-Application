package vyshaliprabananthlal.stream.message;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MovingHoldingTest {

  @Test
  @DisplayName("the message carries the fields the position receiver reads")
  void theMessageCarriesWhatTheReceiverReads() {
    MovingHolding holding = new MovingHolding(340, 100102, 500.0);

    String message = holding.asMessage();

    assertThat(message).contains("\"accountId\":340");
    assertThat(message).contains("\"productId\":100102");
    assertThat(message).contains("\"howMany\":500.0");
  }

  @Test
  @DisplayName("a holding moves at most one percent either way from where it started")
  void aHoldingMovesAtMostOnePercent() {
    MovingHolding holding = new MovingHolding(1, 1, 1000.0);

    for (int tick = 0; tick < 5000; tick++) {
      holding.moveALittle();
      assertThat(holding.rightNow()).isBetween(990.0, 1010.0);
    }
  }

  @Test
  @DisplayName("the message is keyed on the account so one account stays on one partition")
  void theKeyIsTheAccount() {
    assertThat(new MovingHolding(340, 1, 1.0).messageKey()).isEqualTo("340");
  }

  @Test
  @DisplayName("the quantity is cut to four decimals, which is what the column holds")
  void theQuantityIsCutToFourDecimals() {
    MovingHolding holding = new MovingHolding(1, 1, 123.456789);

    assertThat(holding.asMessage()).contains("\"howMany\":123.4568");
  }
}
