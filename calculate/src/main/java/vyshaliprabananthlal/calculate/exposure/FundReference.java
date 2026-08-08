package vyshaliprabananthlal.calculate.exposure;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vyshaliprabananthlal.calculate.sql.Sql;

@Service
public class FundReference {

    private final JdbcTemplate database;
    private final Sql sql;
    private final Counter timesWeActuallyAsked;

    public FundReference(JdbcTemplate database, Sql sql, MeterRegistry meters) {
        this.database = database;
        this.sql = sql;
        this.timesWeActuallyAsked = meters.counter("rtat.reference.read.from.database");
    }

    @Cacheable(CacheConfig.FUND_REPORTING_CURRENCY)
    public String reportingCurrencyOf(int fundId) {
        timesWeActuallyAsked.increment();

        List<String> found = database.queryForList(sql.statement("reporting-currency-of-fund"), String.class, fundId);

        if (found.isEmpty()) {
            throw new IllegalArgumentException("no fund with id " + fundId);
        }
        return found.get(0).trim();
    }

    @Cacheable(CacheConfig.ACCOUNTS_IN_FUND)
    public List<Integer> accountsIn(int fundId) {
        timesWeActuallyAsked.increment();

        return database.queryForList(sql.statement("accounts-in-fund"), Integer.class, fundId);
    }
}
