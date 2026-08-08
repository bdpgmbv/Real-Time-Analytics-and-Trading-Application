package vyshaliprabananthlal.stream.message;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WhatToTradeTest {

  @Test
  @DisplayName("the message carries every field the trade receiver reads")
  void theMessageCarriesWhatTheReceiverReads() {
    String message = new TradeCandidate(340, 100102).newTrade(77);

    assertThat(message).contains("\"tradeId\":77");
    assertThat(message).contains("\"accountId\":340");
    assertThat(message).contains("\"productId\":100102");
    assertThat(message).contains("\"howMany\":");
    assertThat(message).contains("\"price\":");
    assertThat(message).contains("\"happenedAt\":");
    assertThat(message).contains("\"cameFrom\":");
  }

  @Test
  @DisplayName("trades go both ways, some buys and some sells")
  void tradesGoBothWays() {
    TradeCandidate choice = new TradeCandidate(1, 1);

    boolean sawABuy = false;
    boolean sawASale = false;

    for (long tradeNumber = 1; tradeNumber <= 200; tradeNumber++) {
      String message = choice.newTrade(tradeNumber);
      if (message.contains("\"howMany\":-")) {
        sawASale = true;
      } else {
        sawABuy = true;
      }
    }

    assertThat(sawABuy).isTrue();
    assertThat(sawASale).isTrue();
  }

  @Test
  @DisplayName("some trades are typed in by a person and the rest come off the feed")
  void someTradesAreTypedInByHand() {
    TradeCandidate choice = new TradeCandidate(1, 1);

    boolean sawTypedIn = false;
    boolean sawAutomatic = false;

    for (long tradeNumber = 1; tradeNumber <= 500; tradeNumber++) {
      String message = choice.newTrade(tradeNumber);
      if (message.contains("SOMEONE UPLOADED IT")) {
        sawTypedIn = true;
      }
      if (message.contains("AUTOMATIC FEED")) {
        sawAutomatic = true;
      }
    }

    assertThat(sawTypedIn).isTrue();
    assertThat(sawAutomatic).isTrue();
  }

  @Test
  @DisplayName("no trade is ever for nothing")
  void noTradeIsEverForNothing() {
    TradeCandidate choice = new TradeCandidate(1, 1);

    for (long tradeNumber = 1; tradeNumber <= 500; tradeNumber++) {
      assertThat(choice.newTrade(tradeNumber)).doesNotContain("\"howMany\":0,");
    }
  }

  @Test
  @DisplayName("the message is keyed on the account so one account stays on one partition")
  void theKeyIsTheAccount() {
    assertThat(new TradeCandidate(340, 1).messageKey()).isEqualTo("340");
  }
}
