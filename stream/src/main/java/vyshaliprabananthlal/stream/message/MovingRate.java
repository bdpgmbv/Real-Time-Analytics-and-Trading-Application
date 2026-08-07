package vyshaliprabananthlal.stream.message;

import java.util.Random;

public final class MovingRate {

  private static final Random DICE = new Random();

  private final String fromCurrency;
  private final String toCurrency;
  private final double startedAt;

  private double rightNow;

  public MovingRate(String fromCurrency, String toCurrency, double startedAt) {
    this.fromCurrency = fromCurrency;
    this.toCurrency = toCurrency;
    this.startedAt = startedAt;
    this.rightNow = startedAt;
  }

  public void moveALittle() {
    boolean sameCurrencyBothSides = fromCurrency.equals(toCurrency);
    if (sameCurrencyBothSides) {
      return;
    }

    double tinyRandomPush = (DICE.nextDouble() - 0.5) * 0.0004 * startedAt;
    double pullBackTowardsStart = (startedAt - rightNow) * 0.01;

    rightNow = rightNow + tinyRandomPush + pullBackTowardsStart;
  }

  public double rightNow() {
    return rightNow;
  }

  public String messageKey() {
    return fromCurrency + "-" + toCurrency;
  }

  public String asMessage() {
    return String.format(
        "{\"from\":\"%s\",\"to\":\"%s\",\"rate\":%s}", fromCurrency, toCurrency, rightNow);
  }
}
