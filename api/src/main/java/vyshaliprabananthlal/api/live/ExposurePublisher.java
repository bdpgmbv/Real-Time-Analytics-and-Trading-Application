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

    private final ScreenRegistry screens;
    private final MarketChangeFlag changes;
    private final ExposureCalculator calculator;
    private final Counter pushed;
    private final Counter skippedBecauseNothingChanged;

    private final Map<Integer, String> whatWeLastSent = new ConcurrentHashMap<>();

    public ExposurePublisher(
            ScreenRegistry screens, MarketChangeFlag changes, ExposureCalculator calculator, MeterRegistry meters) {

        this.screens = screens;
        this.changes = changes;
        this.calculator = calculator;
        this.pushed = meters.counter("rtat.live.pushed");
        this.skippedBecauseNothingChanged = meters.counter("rtat.live.unchanged");
    }

    /**
     * Deliberately not locked between instances, unlike the folder sweep.
     *
     * <p>Every instance holds its own open connections, and a browser is connected to exactly one
     * of them. An instance that skipped its turn because another held a lock would leave its own
     * screens frozen while somebody else's updated.
     *
     * <p>The rule is which resource is shared. A folder on disk is shared and needs one sweeper.
     * A set of open connections belongs to the instance holding them.
     */
    @Scheduled(fixedDelayString = "${rtat.live.push-every-milliseconds:1000}")
    public void publishWhatMoved() {
        if (screens.watchedFunds().isEmpty()) {
            return;
        }
        if (!changes.hasChanged()) {
            return;
        }

        for (int fundId : screens.watchedFunds()) {
            publishFund(fundId);
        }
    }

    public void publishFund(int fundId) {
        try {
            FundExposure now = calculator.forWholeFund(fundId);
            String asText = signatureOf(now);

            if (asText.equals(whatWeLastSent.get(fundId))) {
                skippedBecauseNothingChanged.increment();
                return;
            }

            whatWeLastSent.put(fundId, asText);
            screens.sendTo(fundId, THE_EVENT_NAME, now);
            pushed.increment();

        } catch (RuntimeException couldNotWorkItOut) {
            LOG.warn("could not push fund {}: {}", fundId, couldNotWorkItOut.getMessage());
        }
    }

    static String signatureOf(FundExposure exposure) {
        StringBuilder builder = new StringBuilder();

        for (var currencyAmount : exposure.byCurrency()) {
            builder.append(currencyAmount.currency())
                    .append('=')
                    .append(Math.round(currencyAmount.amount()))
                    .append(';');
        }
        return builder.toString();
    }

    void clear() {
        whatWeLastSent.clear();
    }
}
