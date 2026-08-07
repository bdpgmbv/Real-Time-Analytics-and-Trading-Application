package vyshaliprabananthlal.stream.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WaitingHedgeTest {

  @Test
  @DisplayName("a whole fill carries every field the fill receiver reads")
  void theMessageCarriesWhatTheReceiverReads() {
    List<String> fills = new WaitingHedge(500, -9000000, "FXM-77120").fillMessages(1, false);

    assertThat(fills).hasSize(1);
    assertThat(fills.get(0)).contains("\"fillId\":1");
    assertThat(fills.get(0)).contains("\"hedgeId\":500");
    assertThat(fills.get(0)).contains("\"amountFilled\":9000000.0");
    assertThat(fills.get(0)).contains("\"rate\":");
    assertThat(fills.get(0)).contains("\"filledAt\":");
    assertThat(fills.get(0)).contains("\"theirReference\":\"FXM-77120-1\"");
  }

  @Test
  @DisplayName("a split fill comes back as two parts that add up to the whole amount")
  void aSplitFillAddsUpToTheWhole() {
    List<String> fills = new WaitingHedge(500, -9000000, "FXM-77120").fillMessages(1, true);

    assertThat(fills).hasSize(2);
    assertThat(amountIn(fills.get(0)) + amountIn(fills.get(1))).isEqualTo(9000000.0);
  }

  @Test
  @DisplayName("an odd amount still splits into two parts that add up exactly")
  void anOddAmountStillAddsUp() {
    List<String> fills = new WaitingHedge(500, -3333333.3333, "FXM-9").fillMessages(1, true);

    assertThat(amountIn(fills.get(0)) + amountIn(fills.get(1))).isEqualTo(3333333.3333);
  }

  @Test
  @DisplayName("the two parts of a split fill get their own fill numbers and references")
  void aSplitFillUsesTwoFillNumbers() {
    List<String> fills = new WaitingHedge(500, -9000000, "FXM-77120").fillMessages(7, true);

    assertThat(fills.get(0))
        .contains("\"fillId\":7")
        .contains("\"theirReference\":\"FXM-77120-7\"");
    assertThat(fills.get(1))
        .contains("\"fillId\":8")
        .contains("\"theirReference\":\"FXM-77120-8\"");
  }

  @Test
  @DisplayName("a sell hedge is filled as a positive amount, not a negative one")
  void aSellHedgeIsFilledAsAPositiveAmount() {
    WaitingHedge sell = new WaitingHedge(500, -4000000, "FXM-1");
    WaitingHedge buy = new WaitingHedge(501, 4000000, "FXM-2");

    assertThat(amountIn(sell.fillMessages(1, false).get(0))).isEqualTo(4000000.0);
    assertThat(amountIn(buy.fillMessages(1, false).get(0))).isEqualTo(4000000.0);
  }

  @Test
  @DisplayName("the message is keyed on the hedge so both parts stay on one partition")
  void theKeyIsTheHedge() {
    assertThat(new WaitingHedge(500, -1, "FXM-1").messageKey()).isEqualTo("500");
  }

  private double amountIn(String message) {
    int startsAt = message.indexOf("\"amountFilled\":") + "\"amountFilled\":".length();
    int endsAt = message.indexOf(',', startsAt);

    return Double.parseDouble(message.substring(startsAt, endsAt));
  }
}
