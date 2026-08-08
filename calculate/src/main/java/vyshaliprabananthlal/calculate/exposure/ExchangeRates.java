package vyshaliprabananthlal.calculate.exposure;

import java.util.HashMap;
import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vyshaliprabananthlal.calculate.sql.Sql;

@Component
public class ExchangeRates {

    private final JdbcTemplate database;
    private final Sql sql;

    public ExchangeRates(JdbcTemplate database, Sql sql) {
        this.database = database;
        this.sql = sql;
    }

    @Cacheable(CacheConfig.EXCHANGE_RATES)
    public Map<String, Double> into(String reportingCurrency) {
        Map<String, Double> rates = new HashMap<>();

        database.query(
                sql.statement("rates-into"),
                row -> {
                    rates.put(row.getString(1).trim(), row.getDouble(2));
                },
                reportingCurrency);

        rates.putIfAbsent(reportingCurrency, 1.0);
        return rates;
    }
}
