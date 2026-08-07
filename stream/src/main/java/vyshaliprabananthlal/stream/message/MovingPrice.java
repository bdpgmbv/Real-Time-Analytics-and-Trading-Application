package vyshaliprabananthlal.stream.message;

import java.util.Random;

public final class MovingPrice {

  private static final Random DICE = new Random();

  private final int productNumber;
  private final double startedAt;

  private double rightNow;

  public MovingPrice(int productNumber, double startedAt) {
    this.productNumber = productNumber;
    this.startedAt = startedAt;
    this.rightNow = startedAt;
  }

  public void moveALittle() {
    double smallMove = (DICE.nextDouble() - 0.5) * 0.01 * startedAt;
    double pullBackTowardsStart = (startedAt - rightNow) * 0.005;

    rightNow = Math.max(0.01, rightNow + smallMove + pullBackTowardsStart);
  }

  public double rightNow() {
    return rightNow;
  }

  public String messageKey() {
    return Integer.toString(productNumber);
  }

  public String asMessage() {
    double rounded = Math.round(rightNow * 1000000) / 1000000.0;

    return String.format(
        "{\"productId\":%d,\"price\":%s,\"howFresh\":\"DELAYED 20 MINUTES\"}",
        productNumber, rounded);
  }
}
