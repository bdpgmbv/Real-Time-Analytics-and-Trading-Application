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

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String EXCHANGE_RATES = "exchange rates";
    public static final String FUND_REPORTING_CURRENCY = "what a fund reports in";
    public static final String ACCOUNTS_IN_FUND = "the accounts in a fund";
    public static final String USER_CLIENT = "which client a user belongs to";

    @Bean
    CacheManager caches(
            @Value("${rtat.cache.rates-for-milliseconds:1000}") long ratesFor,
            @Value("${rtat.cache.reference-for-seconds:300}") long referenceFor) {

        SimpleCacheManager manager = new SimpleCacheManager();

        manager.setCaches(List.of(
                heldFor(EXCHANGE_RATES, Duration.ofMillis(ratesFor), 200),
                heldFor(FUND_REPORTING_CURRENCY, Duration.ofSeconds(referenceFor), 20000),
                heldFor(ACCOUNTS_IN_FUND, Duration.ofSeconds(referenceFor), 20000),
                heldFor(USER_CLIENT, Duration.ofSeconds(referenceFor), 50000)));

        manager.initializeCaches();

        return manager;
    }

    private static CaffeineCache heldFor(String name, Duration howLong, int howMany) {
        return new CaffeineCache(
                name,
                Caffeine.newBuilder()
                        .expireAfterWrite(howLong)
                        .maximumSize(howMany)
                        .recordStats()
                        .build());
    }
}
