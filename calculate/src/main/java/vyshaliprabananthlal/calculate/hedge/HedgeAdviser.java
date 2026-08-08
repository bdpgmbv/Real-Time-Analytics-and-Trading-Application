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

    public List<Recommendation> recommendFor(FundExposure exposure) {
        List<Recommendation> advice = new ArrayList<>();

        for (FundExposure.CurrencyAmount held : exposure.byCurrency()) {
            if (held.currency().equals(exposure.reportingCurrency())) {
                continue;
            }
            if (Math.abs(held.inReportingCurrency()) < tooSmallToBother) {
                continue;
            }

            advice.add(new Recommendation(
                    held.currency(),
                    held.amount(),
                    -held.amount(),
                    FORWARD,
                    reasonFor(held, exposure.reportingCurrency())));
        }
        return advice;
    }

    private String reasonFor(FundExposure.CurrencyAmount held, String reportingCurrency) {
        String direction = held.amount() > 0 ? "holding" : "short";

        return "the fund reports in "
                + reportingCurrency
                + " and is "
                + direction
                + " "
                + Math.abs(Math.round(held.amount()))
                + " "
                + held.currency();
    }

    /** One currency worth hedging, what we suggest doing about it, and why. */
    public record Recommendation(
            String currency, double exposure, double suggestedAmount, String instrument, String reason) {}
}
