package vyshaliprabananthlal.calculate.exposure;

import java.util.List;

public record FundExposure(
    int fundId, String reportingCurrency, List<Exposure> byCurrency, int howManyAccounts) {

  public Exposure forCurrency(String currency) {
    for (Exposure one : byCurrency) {
      if (one.currency().equals(currency)) {
        return one;
      }
    }
    return new Exposure(currency, 0, 0);
  }

  public double total() {
    double total = 0;
    for (Exposure one : byCurrency) {
      total = total + one.amountInReportingCurrency();
    }
    return total;
  }
}
