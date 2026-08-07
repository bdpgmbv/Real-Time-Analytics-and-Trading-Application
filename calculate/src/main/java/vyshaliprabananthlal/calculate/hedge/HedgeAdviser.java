package vyshaliprabananthlal.calculate.hedge;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vyshaliprabananthlal.calculate.exposure.Exposure;
import vyshaliprabananthlal.calculate.exposure.FundExposure;

@Service
public class HedgeAdviser {

  private static final String FORWARD = "FORWARD";

  private final double tooSmallToBother;

  public HedgeAdviser(@Value("${rtat.hedge.too-small-to-bother:100000}") double tooSmallToBother) {
    this.tooSmallToBother = tooSmallToBother;
  }

  public List<Recommendation> whatToHedge(FundExposure exposure) {
    List<Recommendation> advice = new ArrayList<>();

    for (Exposure one : exposure.byCurrency()) {
      if (one.currency().equals(exposure.reportingCurrency())) {
        continue;
      }
      if (Math.abs(one.amountInReportingCurrency()) < tooSmallToBother) {
        continue;
      }

      advice.add(
          new Recommendation(
              one.currency(),
              one.amount(),
              -one.amount(),
              FORWARD,
              whyWeSaidThat(one, exposure.reportingCurrency())));
    }
    return advice;
  }

  private String whyWeSaidThat(Exposure one, String reportingCurrency) {
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
}
