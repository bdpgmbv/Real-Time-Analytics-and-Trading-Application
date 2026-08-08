package vyshaliprabananthlal.calculate.exposure;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vyshaliprabananthlal.platform.sql.SqlStatements;

/**
 * The facts about a fund that every calculation needs and that almost never change.
 *
 * <p>Separate from {@link ExposureCalculator} because Spring's caching works through a proxy: a
 * cached method called from inside its own class goes straight to the code and never reaches the
 * cache. Putting these here is what makes the annotations do anything at all.
 */
@Service
public class FundLookup {

    private final JdbcTemplate database;
    private final SqlStatements statements;
    private final Counter databaseReads;

    public FundLookup(JdbcTemplate database, SqlStatements statements, MeterRegistry meters) {
        this.database = database;
        this.statements = statements;

        // Counts the reads that got past the cache, so the hit rate is visible in Grafana.
        this.databaseReads = meters.counter("rtat.reference.read.from.database");
    }

    @Cacheable(CacheConfig.FUND_REPORTING_CURRENCY)
    public String reportingCurrencyOf(int fundId) {
        databaseReads.increment();

        List<String> found =
                database.queryForList(statements.statement("select-fund-reporting-currency"), String.class, fundId);

        if (found.isEmpty()) {
            throw new IllegalArgumentException("no fund with id " + fundId);
        }
        return found.get(0).trim();
    }

    @Cacheable(CacheConfig.FUND_ACCOUNTS)
    public List<Integer> accountsIn(int fundId) {
        databaseReads.increment();

        return database.queryForList(statements.statement("select-accounts-by-fund"), Integer.class, fundId);
    }
}
