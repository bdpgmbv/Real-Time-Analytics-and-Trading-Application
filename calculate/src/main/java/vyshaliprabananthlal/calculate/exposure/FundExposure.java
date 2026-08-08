package vyshaliprabananthlal.calculate.exposure;

import java.util.List;

/**
 * What a fund is exposed to, one line per currency.
 *
 * <p>Amounts are held in the currency itself and converted when read. An FX rate moving
 * therefore changes what this reports without changing what was stored.
 */
public record FundExposure(int fundId, String reportingCurrency, List<CurrencyAmount> byCurrency, int accountCount) {

    /** Zero rather than null for a currency the fund does not hold. */
    public CurrencyAmount forCurrency(String currency) {
        for (CurrencyAmount held : byCurrency) {
            if (held.currency().equals(currency)) {
                return held;
            }
        }
        return new CurrencyAmount(currency, 0, 0);
    }

    /** Everything added up, in the currency the fund reports in. */
    public double total() {
        double total = 0;
        for (CurrencyAmount held : byCurrency) {
            total += held.inReportingCurrency();
        }
        return total;
    }

    /** How much of one currency, both as held and as the fund reports it. */
    public record CurrencyAmount(String currency, double amount, double inReportingCurrency) {}
}
