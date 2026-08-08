package vyshaliprabananthlal.stream.message;

import java.time.Instant;
import java.util.Random;

public final class TradeCandidate {

  private static final Random DICE = new Random();

  private static final int BIGGEST_TRADE = 10000;
  private static final int ONE_IN_THIS_MANY_IS_TYPED_IN = 10;

  private final int accountNumber;
  private final int productNumber;

  public TradeCandidate(int accountNumber, int productNumber) {
    this.accountNumber = accountNumber;
    this.productNumber = productNumber;
  }

  public String messageKey() {
    return Integer.toString(accountNumber);
  }

  public String newTrade(long tradeNumber) {
    long howMany = 1L + DICE.nextInt(BIGGEST_TRADE);
    boolean itIsASale = DICE.nextInt(2) == 0;
    if (itIsASale) {
      howMany = -howMany;
    }

    double price = Math.round((5 + DICE.nextDouble() * 300) * 100) / 100.0;
    boolean typedInByHand = DICE.nextInt(ONE_IN_THIS_MANY_IS_TYPED_IN) == 0;
    String cameFrom = typedInByHand ? "SOMEONE UPLOADED IT" : "AUTOMATIC FEED";

    return String.format(
        "{\"tradeId\":%d,\"accountId\":%d,\"productId\":%d,\"howMany\":%d,"
            + "\"price\":%s,\"happenedAt\":\"%s\",\"cameFrom\":\"%s\"}",
        tradeNumber, accountNumber, productNumber, howMany, price, Instant.now(), cameFrom);
  }
}
