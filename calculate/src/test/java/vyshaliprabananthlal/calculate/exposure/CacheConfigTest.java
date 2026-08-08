package vyshaliprabananthlal.calculate.exposure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

class WhatWeCacheTest {

  private final CacheManager caches = new CacheConfig().whatWeKeep(1000, 300);

  @Test
  @DisplayName("the three things every calculation re-reads are kept")
  void theThreeRepeatedReadsAreKept() {
    assertThat(caches.getCacheNames())
        .containsExactlyInAnyOrder(
            CacheConfig.EXCHANGE_RATES,
            CacheConfig.FUND_REPORTING_CURRENCY,
            CacheConfig.ACCOUNTS_IN_FUND);
  }

  @Test
  @DisplayName("entitlements are deliberately not kept, because revoking must bite at once")
  void entitlementsAreNotKept() {
    assertThat(caches.getCacheNames()).noneMatch(name -> name.contains("entitle"));
  }

  @Test
  @DisplayName("the exposure answer itself is not kept, only the things it is built from")
  void theAnswerIsNotKept() {
    assertThat(caches.getCacheNames()).noneMatch(name -> name.contains("exposure"));
  }

  @Test
  @DisplayName("a rate put in comes back out")
  void aRatePutInComesBackOut() {
    Cache rates = cacheCalled(caches, CacheConfig.EXCHANGE_RATES);
    rates.put("USD", Map.of("EUR", 1.1));

    assertThat(whatIsIn(rates, "USD")).isEqualTo(Map.of("EUR", 1.1));
  }

  @Test
  @DisplayName("rates asked for in different currencies do not share an entry")
  void differentCurrenciesDoNotShareAnEntry() {
    Cache rates = cacheCalled(caches, CacheConfig.EXCHANGE_RATES);

    rates.put("USD", Map.of("EUR", 1.1));
    rates.put("GBP", Map.of("EUR", 0.85));

    assertThat(whatIsIn(rates, "USD")).isEqualTo(Map.of("EUR", 1.1));
    assertThat(whatIsIn(rates, "GBP")).isEqualTo(Map.of("EUR", 0.85));
  }

  @Test
  @DisplayName("a rate stops being served once it is older than we allow")
  void aStaleRateIsNotServed() throws InterruptedException {
    Cache rates = cacheCalled(new CacheConfig().whatWeKeep(50, 300), CacheConfig.EXCHANGE_RATES);

    rates.put("USD", Map.of("EUR", 1.1));
    Thread.sleep(200);

    assertThat(rates.get("USD")).isNull();
  }

  @Test
  @DisplayName("reference data is kept far longer than rates, because it barely moves")
  void referenceDataOutlivesRates() throws InterruptedException {
    CacheManager mixed = new CacheConfig().whatWeKeep(50, 300);
    Cache rates = cacheCalled(mixed, CacheConfig.EXCHANGE_RATES);
    Cache accounts = cacheCalled(mixed, CacheConfig.ACCOUNTS_IN_FUND);

    rates.put("USD", Map.of("EUR", 1.1));
    accounts.put(1, List.of(10, 11));

    Thread.sleep(200);

    assertThat(rates.get("USD")).isNull();
    assertThat(whatIsIn(accounts, 1)).isEqualTo(List.of(10, 11));
  }

  private static Cache cacheCalled(CacheManager manager, String name) {
    Cache found = manager.getCache(name);

    if (found == null) {
      throw new AssertionError("no cache called " + name);
    }
    return found;
  }

  private static Object whatIsIn(Cache cache, Object key) {
    Cache.ValueWrapper held = cache.get(key);

    if (held == null) {
      throw new AssertionError("nothing held under " + key);
    }

    Object value = held.get();

    if (value == null) {
      throw new AssertionError("null held under " + key);
    }
    return value;
  }
}
