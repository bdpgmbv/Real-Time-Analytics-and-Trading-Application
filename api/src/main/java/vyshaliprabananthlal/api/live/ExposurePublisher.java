package vyshaliprabananthlal.api.live;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    private final Counter sweepsThatOverran;
    private final ExecutorService recalculators;
    private final Duration oneSweepBudget;

    private final Map<Integer, String> whatWeLastSent = new ConcurrentHashMap<>();

    public ExposurePublisher(
            ScreenRegistry screens,
            MarketChangeFlag changes,
            ExposureCalculator calculator,
            MeterRegistry meters,
            @Value("${rtat.live.recalculate-threads:8}") int howManyThreads,
            @Value("${rtat.live.push-every-milliseconds:1000}") long sweepBudgetMillis) {

        this.screens = screens;
        this.changes = changes;
        this.calculator = calculator;
        this.pushed = meters.counter("rtat.live.pushed");
        this.skippedBecauseNothingChanged = meters.counter("rtat.live.unchanged");
        this.sweepsThatOverran = meters.counter("rtat.live.sweep.overran");
        this.oneSweepBudget = Duration.ofMillis(sweepBudgetMillis);

        // Named, so a thread dump says which pool a stuck thread belongs to.
        AtomicInteger nextNumber = new AtomicInteger(1);
        this.recalculators = Executors.newFixedThreadPool(
                howManyThreads,
                runnable -> new Thread(runnable, "exposure-recalculator-" + nextNumber.getAndIncrement()));
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

        recalculateInParallel(screens.watchedFunds());
    }

    /**
     * One fund at a time was the limit on how many screens a node could serve.
     *
     * <p>A calculation takes about fourteen milliseconds, so a sweep every second could manage
     * roughly seventy funds before it overran its own interval and the screens started lagging.
     * The work is independent per fund and spends nearly all its time waiting on the database,
     * so it parallelises almost perfectly.
     *
     * <p>The pool is bounded and smaller than the connection pool on purpose. Unbounded threads
     * would simply move the queue from here to the database, where it is harder to see.
     */
    private void recalculateInParallel(Set<Integer> funds) {
        CountDownLatch allDone = new CountDownLatch(funds.size());

        for (int fundId : funds) {
            recalculators.execute(() -> {
                try {
                    publishFund(fundId);
                } finally {
                    allDone.countDown();
                }
            });
        }

        try {
            // If a sweep cannot finish inside its own interval, let the next one start rather
            // than piling sweeps on top of each other.
            if (!allDone.await(oneSweepBudget.toMillis(), TimeUnit.MILLISECONDS)) {
                LOG.warn("a sweep did not finish within {}", oneSweepBudget);
                sweepsThatOverran.increment();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    void stopRecalculating() {
        recalculators.shutdown();
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
