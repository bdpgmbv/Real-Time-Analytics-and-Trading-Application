package vyshaliprabananthlal.stream.message;

import java.util.Random;

public final class MovingRate {

  private static final Random RANDOM = new Random();

  private final String fromCurrency;
  private final String toCurrency;
  private final double startedAt;

  private double currentPrice;

  public MovingRate(String fromCurrency, String toCurrency, double startedAt) {
    this.fromCurrency = fromCurrency;
    this.toCurrency = toCurrency;
    this.startedAt = startedAt;
    this.currentPrice = startedAt;
  }

  public void move() {
    boolean sameCurrencyBothSides = fromCurrency.equals(toCurrency);
    if (sameCurrencyBothSides) {
      return;
    }

    double tinyRandomPush = (RANDOM.nextDouble() - 0.5) * 0.0004 * startedAt;
    double pullBackTowardsStart = (startedAt - currentPrice) * 0.01;

    currentPrice = currentPrice + tinyRandomPush + pullBackTowardsStart;
  }

  public double currentPrice() {
    return currentPrice;
  }

  public String messageKey() {
    return fromCurrency + "-" + toCurrency;
  }

  public String asMessage() {
    return String.format(
        "{\"from\":\"%s\",\"to\":\"%s\",\"rate\":%s}", fromCurrency, toCurrency, currentPrice);
  }
}
