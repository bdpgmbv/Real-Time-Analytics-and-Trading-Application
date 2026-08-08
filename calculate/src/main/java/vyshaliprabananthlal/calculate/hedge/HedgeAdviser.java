package vyshaliprabananthlal.calculate.hedge;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vyshaliprabananthlal.calculate.exposure.FundExposure;

/**
 * Turns exposure into a suggestion: which currencies to hedge, and by how much.
 *
 * <p>It only ever suggests. What the client actually chose is a separate decision, recorded by
 * {@link HedgeBook}, and the two are kept apart because a person has to answer for the difference.
 */
@Service
public class HedgeAdviser {

    private static final String FORWARD = "FORWARD";

    private final double minimumWorthHedging;

    public HedgeAdviser(@Value("${rtat.hedge.too-small-to-bother:100000}") double minimumWorthHedging) {
        this.minimumWorthHedging = minimumWorthHedging;
    }

    public List<Recommendation> recommendFor(FundExposure exposure) {
        List<Recommendation> advice = new ArrayList<>();

        for (FundExposure.CurrencyAmount held : exposure.byCurrency()) {

            // A fund is not exposed to the currency it reports in.
            if (held.currency().equals(exposure.reportingCurrency())) {
                continue;
            }

            // Judged in the reporting currency, so ten million yen is not mistaken for a lot.
            if (Math.abs(held.inReportingCurrency()) < minimumWorthHedging) {
                continue;
            }

            // Hedging means taking the opposite side, so the suggestion is the negative.
            advice.add(new Recommendation(
                    held.currency(),
                    held.amount(),
                    -held.amount(),
                    FORWARD,
                    reasonFor(held, exposure.reportingCurrency())));
        }
        return advice;
    }

    /** Written for the person deciding, so it says which way round it is. */
    private String reasonFor(FundExposure.CurrencyAmount held, String reportingCurrency) {
        String direction = held.amount() > 0 ? "holding" : "short";

        return "the fund reports in " + reportingCurrency
                + " and is " + direction
                + " " + Math.abs(Math.round(held.amount()))
                + " " + held.currency();
    }

    /** One currency worth hedging, what we suggest doing about it, and why. */
    public record Recommendation(
            String currency, double exposure, double suggestedAmount, String instrument, String reason) {}
}
