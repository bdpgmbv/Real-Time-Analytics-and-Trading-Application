package vyshaliprabananthlal.calculate.exposure;

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
  private final Sql sql;

  public ExposureCalculator(JdbcTemplate database, ExchangeRates exchangeRates, Sql sql) {
    this.database = database;
    this.exchangeRates = exchangeRates;
    this.sql = sql;
  }

  public FundExposure forWholeFund(int fundId) {
    return forAccounts(fundId, accountsIn(fundId));
  }

  public FundExposure forAccounts(int fundId, List<Integer> accountIds) {
    String reportingCurrency = reportingCurrencyOf(fundId);

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

  public List<Integer> accountsIn(int fundId) {
    return database.queryForList(sql.statement("accounts-in-fund"), Integer.class, fundId);
  }

  private String reportingCurrencyOf(int fundId) {
    List<String> found =
        database.queryForList(sql.statement("reporting-currency-of-fund"), String.class, fundId);

    if (found.isEmpty()) {
      throw new IllegalArgumentException("no fund with id " + fundId);
    }
    return found.get(0).trim();
  }
}
