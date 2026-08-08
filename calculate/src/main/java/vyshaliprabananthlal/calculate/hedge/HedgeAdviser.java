package vyshaliprabananthlal.calculate.hedge;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vyshaliprabananthlal.calculate.exposure.FundExposure;

@Service
public class HedgeAdviser {

    private static final String FORWARD = "FORWARD";

    private final double tooSmallToBother;

    public HedgeAdviser(@Value("${rtat.hedge.too-small-to-bother:100000}") double tooSmallToBother) {
        this.tooSmallToBother = tooSmallToBother;
    }

    public List<HedgeAdviser.Recommendation> recommendFor(FundExposure exposure) {
        List<HedgeAdviser.Recommendation> advice = new ArrayList<>();

        for (FundExposure.CurrencyAmount one : exposure.byCurrency()) {
            if (one.currency().equals(exposure.reportingCurrency())) {
                continue;
            }
            if (Math.abs(one.inReportingCurrency()) < tooSmallToBother) {
                continue;
            }

            advice.add(new HedgeAdviser.Recommendation(
                    one.currency(),
                    one.amount(),
                    -one.amount(),
                    FORWARD,
                    reasonFor(one, exposure.reportingCurrency())));
        }
        return advice;
    }

    private String reasonFor(FundExposure.CurrencyAmount one, String reportingCurrency) {
        String direction = one.amount() > 0 ? "holding" : "short";

        return "the fund reports in "
                + reportingCurrency
                + " and is "
                + direction
                + " "
                + Math.abs(Math.round(one.amount()))
                + " "
                + one.currency();
    }

    /** One currency worth hedging, what we suggest doing about it, and why. */
    public record Recommendation(
            String currency, double exposure, double suggestedAmount, String instrument, String reason) {}
}
