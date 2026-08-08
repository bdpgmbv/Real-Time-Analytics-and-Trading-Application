package vyshaliprabananthlal.calculate.exposure;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vyshaliprabananthlal.platform.sql.SqlStatements;

/**
 * What a fund is exposed to, worked out from the positions it holds right now.
 *
 * <p>Nothing is stored. A position's value is its quantity times today's price, and the fund's
 * exposure is those values added up per currency. Storing the answer would mean rewriting
 * millions of rows every time a price moved; asking the question costs milliseconds.
 *
 * <p>Two rules live in the SQL rather than here. A security contributes to its own currency at
 * 100%, and any {@code position_exposure} rows add further currencies on top rather than
 * replacing it.
 */
@Service
public class ExposureCalculator {

    private final JdbcTemplate database;
    private final ExchangeRates exchangeRates;
    private final FundLookup funds;
    private final String exposureByCurrency;
    private final Timer calculationTimer;

    public ExposureCalculator(
            JdbcTemplate database,
            ExchangeRates exchangeRates,
            FundLookup funds,
            SqlStatements statements,
            MeterRegistry meters) {

        this.database = database;
        this.exchangeRates = exchangeRates;
        this.funds = funds;
        this.exposureByCurrency = statements.statement("select-exposure-by-currency");
        this.calculationTimer = Timer.builder("rtat.exposure.calculated")
                .publishPercentileHistogram()
                .register(meters);
    }

    /** Every account in the fund. */
    public FundExposure forWholeFund(int fundId) {
        return forAccounts(fundId, funds.accountsIn(fundId));
    }

    /** Only the accounts given, for a screen where somebody has picked a few. */
    public FundExposure forAccounts(int fundId, List<Integer> accountIds) {
        Timer.Sample timing = Timer.start();

        try {
            return calculate(fundId, accountIds);
        } finally {
            timing.stop(calculationTimer);
        }
    }

    private FundExposure calculate(int fundId, List<Integer> accountIds) {
        String reportingCurrency = funds.reportingCurrencyOf(fundId);

        if (accountIds.isEmpty()) {
            return new FundExposure(fundId, reportingCurrency, List.of(), 0);
        }

        Map<String, Double> rates = exchangeRates.into(reportingCurrency);
        List<FundExposure.CurrencyAmount> exposures = new ArrayList<>();

        database.query(
                exposureByCurrency,
                row -> {
                    String currency = row.getString(1).trim();
                    double amount = row.getDouble(2);

                    // A currency with no rate today converts to zero rather than guessing.
                    double rate = rates.getOrDefault(currency, 0.0);

                    exposures.add(new FundExposure.CurrencyAmount(currency, amount, amount * rate));
                },
                (Object) accountIds.toArray(new Integer[0]));

        return new FundExposure(fundId, reportingCurrency, exposures, accountIds.size());
    }
}
