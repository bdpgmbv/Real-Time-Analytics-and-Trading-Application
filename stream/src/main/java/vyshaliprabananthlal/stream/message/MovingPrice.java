package vyshaliprabananthlal.stream.message;

import java.util.Random;

public final class MovingPrice {

  private static final Random RANDOM = new Random();

  private final int productNumber;
  private final double startedAt;

  private double currentPrice;

  public MovingPrice(int productNumber, double startedAt) {
    this.productNumber = productNumber;
    this.startedAt = startedAt;
    this.currentPrice = startedAt;
  }

  public void move() {
    double smallMove = (RANDOM.nextDouble() - 0.5) * 0.01 * startedAt;
    double pullBackTowardsStart = (startedAt - currentPrice) * 0.005;

    currentPrice = Math.max(0.01, currentPrice + smallMove + pullBackTowardsStart);
  }

  public double currentPrice() {
    return currentPrice;
  }

  public String messageKey() {
    return Integer.toString(productNumber);
  }

  public String asMessage() {
    double rounded = Math.round(currentPrice * 1000000) / 1000000.0;

    return String.format(
        "{\"productId\":%d,\"price\":%s,\"howFresh\":\"DELAYED 20 MINUTES\"}",
        productNumber, rounded);
  }
}
