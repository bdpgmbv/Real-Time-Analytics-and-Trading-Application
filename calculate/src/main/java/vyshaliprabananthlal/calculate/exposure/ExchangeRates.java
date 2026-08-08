package vyshaliprabananthlal.calculate.exposure;

import java.util.HashMap;
import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vyshaliprabananthlal.platform.sql.SqlStatements;

/** Today's rate from every currency into one, ready to multiply by. */
@Component
public class ExchangeRates {

    private final JdbcTemplate database;
    private final String ratesIntoCurrency;

    public ExchangeRates(JdbcTemplate database, SqlStatements statements) {
        this.database = database;
        this.ratesIntoCurrency = statements.statement("select-fx-rates-into-currency");
    }

    /**
     * Every rate that converts into {@code targetCurrency}, keyed by the currency it converts from.
     *
     * <p>The target converts to itself at exactly 1. That is added here rather than stored, so a
     * stale row can never make a fund's own currency worth something other than itself.
     */
    @Cacheable(CacheConfig.FX_RATES)
    public Map<String, Double> into(String targetCurrency) {
        Map<String, Double> rates = new HashMap<>();

        database.query(
                ratesIntoCurrency,
                row -> {
                    rates.put(row.getString(1).trim(), row.getDouble(2));
                },
                targetCurrency);

        rates.putIfAbsent(targetCurrency, 1.0);
        return rates;
    }
}
