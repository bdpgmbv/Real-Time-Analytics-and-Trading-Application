package vyshaliprabananthlal.calculate.exposure;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The few things every exposure calculation re-reads and that barely change.
 *
 * <p>Held in memory rather than in Redis because the data is small, the same on every instance,
 * and a network hop would cost more than the query it replaces.
 *
 * <p>Two things are deliberately absent. The exposure itself, because that is the question being
 * asked and the thing that moves. And entitlements, because the API promises that revoking access
 * takes effect on the next call, and a cache would quietly turn that into "within five minutes".
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Thirty rows, read on every calculation. A second of drift is invisible on a screen. */
    public static final String FX_RATES = "fxRates";

    /** Changes when somebody sets up a fund, which is close to never. */
    public static final String FUND_REPORTING_CURRENCY = "fundReportingCurrency";

    /** Changes when an account is opened or closed. */
    public static final String FUND_ACCOUNTS = "fundAccounts";

    /** Which client a signed-in user belongs to. Fixed for the life of the user. */
    public static final String USER_CLIENT = "userClient";

    private static final int ENOUGH_FOR_EVERY_CURRENCY_PAIR = 200;
    private static final int ENOUGH_FOR_EVERY_FUND = 20_000;
    private static final int ENOUGH_FOR_EVERY_USER = 50_000;

    @Bean
    CacheManager caches(
            @Value("${rtat.cache.rates-for-milliseconds:1000}") long rateLifetimeMillis,
            @Value("${rtat.cache.reference-for-seconds:300}") long referenceLifetimeSeconds) {

        Duration rateLifetime = Duration.ofMillis(rateLifetimeMillis);
        Duration referenceLifetime = Duration.ofSeconds(referenceLifetimeSeconds);

        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                expiringAfter(FX_RATES, rateLifetime, ENOUGH_FOR_EVERY_CURRENCY_PAIR),
                expiringAfter(FUND_REPORTING_CURRENCY, referenceLifetime, ENOUGH_FOR_EVERY_FUND),
                expiringAfter(FUND_ACCOUNTS, referenceLifetime, ENOUGH_FOR_EVERY_FUND),
                expiringAfter(USER_CLIENT, referenceLifetime, ENOUGH_FOR_EVERY_USER)));

        manager.initializeCaches();
        return manager;
    }

    private static CaffeineCache expiringAfter(String name, Duration lifetime, int maximumEntries) {
        return new CaffeineCache(
                name,
                Caffeine.newBuilder()
                        .expireAfterWrite(lifetime)
                        .maximumSize(maximumEntries)
                        .recordStats()
                        .build());
    }
}
