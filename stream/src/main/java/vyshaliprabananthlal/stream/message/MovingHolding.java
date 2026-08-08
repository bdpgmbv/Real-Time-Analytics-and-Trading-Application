package vyshaliprabananthlal.stream.message;

import java.util.Random;

public final class MovingHolding {

  private static final Random RANDOM = new Random();

  private final int accountNumber;
  private final int productNumber;
  private final double startedAt;

  private double currentPrice;

  public MovingHolding(int accountNumber, int productNumber, double startedAt) {
    this.accountNumber = accountNumber;
    this.productNumber = productNumber;
    this.startedAt = startedAt;
    this.currentPrice = startedAt;
  }

  public void move() {
    double smallMove = (RANDOM.nextDouble() - 0.5) * 0.02 * startedAt;

    currentPrice = startedAt + smallMove;
  }

  public double currentPrice() {
    return currentPrice;
  }

  public String messageKey() {
    return Integer.toString(accountNumber);
  }

  public String asMessage() {
    double rounded = Math.round(currentPrice * 10000) / 10000.0;

    return String.format(
        "{\"accountId\":%d,\"productId\":%d,\"howMany\":%s}",
        accountNumber, productNumber, rounded);
  }
}
