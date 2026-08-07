package vyshaliprabananthlal.stream.message;

import java.util.Random;

public final class MovingHolding {

  private static final Random DICE = new Random();

  private final int accountNumber;
  private final int productNumber;
  private final double startedAt;

  private double rightNow;

  public MovingHolding(int accountNumber, int productNumber, double startedAt) {
    this.accountNumber = accountNumber;
    this.productNumber = productNumber;
    this.startedAt = startedAt;
    this.rightNow = startedAt;
  }

  public void moveALittle() {
    double smallMove = (DICE.nextDouble() - 0.5) * 0.02 * startedAt;

    rightNow = startedAt + smallMove;
  }

  public double rightNow() {
    return rightNow;
  }

  public String messageKey() {
    return Integer.toString(accountNumber);
  }

  public String asMessage() {
    double rounded = Math.round(rightNow * 10000) / 10000.0;

    return String.format(
        "{\"accountId\":%d,\"productId\":%d,\"howMany\":%s}",
        accountNumber, productNumber, rounded);
  }
}
