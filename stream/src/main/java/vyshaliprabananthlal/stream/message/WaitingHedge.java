package vyshaliprabananthlal.stream.message;

import java.time.Instant;
import java.util.List;
import java.util.Random;

public final class WaitingHedge {

  private static final Random DICE = new Random();

  private static final double THE_FIRST_PART_OF_A_SPLIT = 0.4;

  private final long hedgeNumber;
  private final double howMuchWasAskedFor;
  private final String theirReference;

  public WaitingHedge(long hedgeNumber, double howMuchWasAskedFor, String theirReference) {
    this.hedgeNumber = hedgeNumber;
    this.howMuchWasAskedFor = howMuchWasAskedFor;
    this.theirReference = theirReference;
  }

  public String messageKey() {
    return Long.toString(hedgeNumber);
  }

  public List<String> fillMessages(long firstFillNumber, boolean inTwoParts) {
    double wholeAmount = Math.abs(howMuchWasAskedFor);

    if (!inTwoParts) {
      return List.of(oneFill(firstFillNumber, wholeAmount));
    }

    double firstPart = Math.round(wholeAmount * THE_FIRST_PART_OF_A_SPLIT * 10000) / 10000.0;
    double secondPart = Math.round((wholeAmount - firstPart) * 10000) / 10000.0;

    return List.of(oneFill(firstFillNumber, firstPart), oneFill(firstFillNumber + 1, secondPart));
  }

  private String oneFill(long fillNumber, double amount) {
    double rate = Math.round((0.9 + DICE.nextDouble() * 0.5) * 100000) / 100000.0;

    return String.format(
        "{\"fillId\":%d,\"hedgeId\":%d,\"amountFilled\":%s,\"rate\":%s,"
            + "\"filledAt\":\"%s\",\"theirReference\":\"%s-%d\"}",
        fillNumber, hedgeNumber, amount, rate, Instant.now(), theirReference, fillNumber);
  }
}
