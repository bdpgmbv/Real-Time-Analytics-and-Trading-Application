package vyshaliprabananthlal.api.live;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vyshaliprabananthlal.calculate.exposure.ExposureCalculator;
import vyshaliprabananthlal.calculate.exposure.FundExposure;

@Component
public class ExposurePublisher {

  private static final Logger LOG = LoggerFactory.getLogger(ExposurePublisher.class);

  static final String THE_EVENT_NAME = "exposure";

  private final ScreenRegistry watching;
  private final MarketChangeFlag changes;
  private final ExposureCalculator calculator;
  private final Counter pushed;
  private final Counter skippedBecauseNothingChanged;

  private final Map<Integer, String> whatWeLastSent = new ConcurrentHashMap<>();

  public ExposurePublisher(
      ScreenRegistry watching,
      MarketChangeFlag changes,
      ExposureCalculator calculator,
      MeterRegistry meters) {

    this.watching = watching;
    this.changes = changes;
    this.calculator = calculator;
    this.pushed = meters.counter("rtat.live.pushed");
    this.skippedBecauseNothingChanged = meters.counter("rtat.live.unchanged");
  }

  @Scheduled(fixedDelayString = "${rtat.live.push-every-milliseconds:1000}")
  public void pushWhateverMoved() {
    if (watching.fundsBeingWatched().isEmpty()) {
      return;
    }
    if (!changes.anythingMoved()) {
      return;
    }

    for (int fundId : watching.fundsBeingWatched()) {
      pushOneFund(fundId);
    }
  }

  public void pushOneFund(int fundId) {
    try {
      FundExposure now = calculator.forWholeFund(fundId);
      String asText = shortDescriptionOf(now);

      if (asText.equals(whatWeLastSent.get(fundId))) {
        skippedBecauseNothingChanged.increment();
        return;
      }

      whatWeLastSent.put(fundId, asText);
      watching.sendTo(fundId, THE_EVENT_NAME, now);
      pushed.increment();

    } catch (RuntimeException couldNotWorkItOut) {
      LOG.warn("could not push fund {}: {}", fundId, couldNotWorkItOut.getMessage());
    }
  }

  static String shortDescriptionOf(FundExposure exposure) {
    StringBuilder builder = new StringBuilder();

    for (var one : exposure.byCurrency()) {
      builder.append(one.currency()).append('=').append(Math.round(one.amount())).append(';');
    }
    return builder.toString();
  }

  void forgetWhatWeSent() {
    whatWeLastSent.clear();
  }
}
