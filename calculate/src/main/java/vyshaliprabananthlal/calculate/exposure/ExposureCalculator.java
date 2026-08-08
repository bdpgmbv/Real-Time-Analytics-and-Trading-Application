package vyshaliprabananthlal.calculate.exposure;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vyshaliprabananthlal.calculate.sql.Sql;

@Service
public class ExposureCalculator {

  private final JdbcTemplate database;
  private final ExchangeRates exchangeRates;
  private final FundFacts fundFacts;
  private final Sql sql;
  private final Timer howLongItTakes;

  public ExposureCalculator(
      JdbcTemplate database,
      ExchangeRates exchangeRates,
      FundFacts fundFacts,
      Sql sql,
      MeterRegistry meters) {

    this.database = database;
    this.exchangeRates = exchangeRates;
    this.fundFacts = fundFacts;
    this.sql = sql;
    this.howLongItTakes =
        Timer.builder("rtat.exposure.calculated").publishPercentileHistogram().register(meters);
  }

  public FundExposure forWholeFund(int fundId) {
    return forAccounts(fundId, fundFacts.accountsIn(fundId));
  }

  public FundExposure forAccounts(int fundId, List<Integer> accountIds) {
    Timer.Sample timing = Timer.start();

    try {
      return workItOut(fundId, accountIds);
    } finally {
      timing.stop(howLongItTakes);
    }
  }

  private FundExposure workItOut(int fundId, List<Integer> accountIds) {
    String reportingCurrency = fundFacts.reportingCurrencyOf(fundId);

    if (accountIds.isEmpty()) {
      return new FundExposure(fundId, reportingCurrency, List.of(), 0);
    }

    Map<String, Double> rates = exchangeRates.into(reportingCurrency);
    List<Exposure> found = new ArrayList<>();

    database.query(
        sql.statement("exposure-by-currency"),
        row -> {
          String currency = row.getString(1).trim();
          double amount = row.getDouble(2);
          double rate = rates.getOrDefault(currency, 0.0);

          found.add(new Exposure(currency, amount, amount * rate));
        },
        (Object) accountIds.toArray(new Integer[0]));

    return new FundExposure(fundId, reportingCurrency, found, accountIds.size());
  }
}
