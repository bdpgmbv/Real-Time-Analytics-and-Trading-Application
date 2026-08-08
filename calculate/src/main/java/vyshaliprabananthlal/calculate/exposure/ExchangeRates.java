package vyshaliprabananthlal.calculate.exposure;

import java.util.HashMap;
import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vyshaliprabananthlal.platform.sql.SqlStatements;

@Component
public class ExchangeRates {

    private final JdbcTemplate database;
    private final SqlStatements statements;

    public ExchangeRates(JdbcTemplate database, SqlStatements statements) {
        this.database = database;
        this.statements = statements;
    }

    @Cacheable(CacheConfig.EXCHANGE_RATES)
    public Map<String, Double> into(String reportingCurrency) {
        Map<String, Double> rates = new HashMap<>();

        database.query(
                statements.statement("select-fx-rates-into-currency"),
                row -> {
                    rates.put(row.getString(1).trim(), row.getDouble(2));
                },
                reportingCurrency);

        rates.putIfAbsent(reportingCurrency, 1.0);
        return rates;
    }
}
