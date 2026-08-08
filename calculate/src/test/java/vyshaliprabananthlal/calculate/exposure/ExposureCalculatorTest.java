package vyshaliprabananthlal.calculate.exposure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import vyshaliprabananthlal.calculate.sql.Sql;

class ExposureCalculatorTest {

  private static JdbcTemplate database;

  private ExposureCalculator calculator;

  @BeforeAll
  static void buildTheSchema() {
    database = RealDatabase.readyToUse();
  }

  @BeforeEach
  void oneFundReportingInUsdHoldingEuropeanShares() {
    Sql sql = new Sql();
    calculator =
        new ExposureCalculator(
            database, new ExchangeRates(database, sql), sql, new SimpleMeterRegistry());

    database.execute(
        "TRUNCATE position_exposure, position, price, product, account, fund, client,"
            + " fx_rate, currency CASCADE");
    database.execute(
        "INSERT INTO currency VALUES ('USD','US Dollar',2),('EUR','Euro',2),"
            + "('GBP','Pound',2),('JPY','Yen',0),('CHF','Franc',2)");
    database.execute(
        "INSERT INTO fx_rate (from_currency,to_currency,rate,rate_date,where_from) VALUES"
            + " ('USD','USD',1.0,CURRENT_DATE,'SEED'),"
            + " ('EUR','USD',1.10,CURRENT_DATE,'SEED'),"
            + " ('GBP','USD',1.25,CURRENT_DATE,'SEED'),"
            + " ('JPY','USD',0.0064,CURRENT_DATE,'SEED'),"
            + " ('CHF','USD',1.15,CURRENT_DATE,'SEED')");
    database.execute("INSERT INTO client (name,size,region) VALUES ('Test','LARGE','EUROPE')");
    database.execute(
        "INSERT INTO fund (fund_id,client_id,name,reporting_currency)"
            + " OVERRIDING SYSTEM VALUE SELECT 1, client_id, 'Fund', 'USD' FROM client");
    database.execute(
        "INSERT INTO account (account_id,fund_id,name) OVERRIDING SYSTEM VALUE"
            + " VALUES (10,1,'Account A'),(11,1,'Account B')");
    database.execute(
        "INSERT INTO product (product_id,kind,name,currency,identifier) OVERRIDING SYSTEM VALUE"
            + " VALUES (100,'SHARES','Airbus','EUR','000000100'),"
            + "        (101,'SHARES','Shell','GBP','000000101'),"
            + "        (102,'SHARES','Sony','JPY','000000102')");
    database.execute(
        "INSERT INTO price (product_id,price,how_fresh,price_date,arrived_at) VALUES"
            + " (100,100.0,'EOD',CURRENT_DATE,now()),"
            + " (101,50.0,'EOD',CURRENT_DATE,now()),"
            + " (102,2000.0,'EOD',CURRENT_DATE,now())");
  }

  @Test
  @DisplayName("a share priced in euros gives euro exposure of quantity times price")
  void oneHoldingGivesOneExposure() {
    hold(10, 100, 500);

    FundExposure exposure = calculator.forWholeFund(1);

    assertThat(exposure.forCurrency("EUR").amount()).isEqualTo(50000.0);
    assertThat(exposure.reportingCurrency()).isEqualTo("USD");
  }

  @Test
  @DisplayName("the exposure is converted into the currency the fund reports in")
  void exposureIsConvertedIntoTheReportingCurrency() {
    hold(10, 100, 500);

    FundExposure exposure = calculator.forWholeFund(1);

    assertThat(exposure.forCurrency("EUR").amountInReportingCurrency())
        .isCloseTo(55000.0, within(0.01));
  }

  @Test
  @DisplayName("holdings in different currencies are kept apart, not added together")
  void differentCurrenciesStayApart() {
    hold(10, 100, 500);
    hold(10, 101, 200);
    hold(10, 102, 30);

    FundExposure exposure = calculator.forWholeFund(1);

    assertThat(exposure.forCurrency("EUR").amount()).isEqualTo(50000.0);
    assertThat(exposure.forCurrency("GBP").amount()).isEqualTo(10000.0);
    assertThat(exposure.forCurrency("JPY").amount()).isEqualTo(60000.0);
    assertThat(exposure.byCurrency()).hasSize(3);
  }

  @Test
  @DisplayName("the same product held in two accounts adds up into one currency total")
  void twoAccountsAddUp() {
    hold(10, 100, 500);
    hold(11, 100, 300);

    FundExposure exposure = calculator.forWholeFund(1);

    assertThat(exposure.forCurrency("EUR").amount()).isEqualTo(80000.0);
    assertThat(exposure.howManyAccounts()).isEqualTo(2);
  }

  @Test
  @DisplayName("asking for one account gives that account only, not the whole fund")
  void oneAccountIsNotTheWholeFund() {
    hold(10, 100, 500);
    hold(11, 100, 300);

    FundExposure justOne = calculator.forAccounts(1, List.of(10));

    assertThat(justOne.forCurrency("EUR").amount()).isEqualTo(50000.0);
    assertThat(justOne.howManyAccounts()).isEqualTo(1);
  }

  @Test
  @DisplayName("a specific exposure adds a second currency on top, it does not replace the first")
  void aSpecificExposureAddsOnTop() {
    hold(10, 100, 500);
    database.execute(
        "INSERT INTO position_exposure (account_id,product_id,slot,currency,percentage)"
            + " VALUES (10,100,1,'CHF',30)");

    FundExposure exposure = calculator.forWholeFund(1);

    assertThat(exposure.forCurrency("EUR").amount()).isEqualTo(50000.0);
    assertThat(exposure.forCurrency("CHF").amount()).isEqualTo(15000.0);
  }

  @Test
  @DisplayName("the same product in two accounts can carry different specific exposures")
  void specificExposureIsPerPositionNotPerProduct() {
    hold(10, 100, 500);
    hold(11, 100, 500);
    database.execute(
        "INSERT INTO position_exposure (account_id,product_id,slot,currency,percentage)"
            + " VALUES (10,100,1,'CHF',30),(11,100,1,'CHF',50)");

    FundExposure exposure = calculator.forWholeFund(1);

    assertThat(exposure.forCurrency("CHF").amount()).isEqualTo(40000.0);
  }

  @Test
  @DisplayName("a price typed in by hand wins over the price that came off the feed")
  void aTypedInPriceWins() {
    hold(10, 100, 500);
    database.execute("UPDATE position SET price_typed_in = 120 WHERE account_id=10");

    FundExposure exposure = calculator.forWholeFund(1);

    assertThat(exposure.forCurrency("EUR").amount()).isEqualTo(60000.0);
  }

  @Test
  @DisplayName("a fund holding nothing has no exposure rather than failing")
  void aFundHoldingNothing() {
    FundExposure exposure = calculator.forWholeFund(1);

    assertThat(exposure.byCurrency()).isEmpty();
    assertThat(exposure.total()).isZero();
  }

  @Test
  @DisplayName("a short position gives negative exposure, not a missing one")
  void aShortPositionIsNegative() {
    hold(10, 100, -500);

    FundExposure exposure = calculator.forWholeFund(1);

    assertThat(exposure.forCurrency("EUR").amount()).isEqualTo(-50000.0);
  }

  @Test
  @DisplayName("a product with no price today is left out rather than counted as zero")
  void aProductWithNoPriceTodayIsLeftOut() {
    hold(10, 100, 500);
    database.execute("DELETE FROM price WHERE product_id = 100");

    FundExposure exposure = calculator.forWholeFund(1);

    assertThat(exposure.byCurrency()).isEmpty();
  }

  @Test
  @DisplayName("asking about a fund that does not exist says so plainly")
  void anUnknownFundIsRefused() {
    assertThatThrownBy(() -> calculator.forWholeFund(999))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no fund with id 999");
  }

  @Test
  @DisplayName("the reporting currency converts at exactly one, never at a stale rate")
  void theReportingCurrencyConvertsAtOne() {
    database.execute(
        "INSERT INTO product (product_id,kind,name,currency,identifier) OVERRIDING SYSTEM VALUE"
            + " VALUES (103,'SHARES','Apple','USD','000000103')");
    database.execute(
        "INSERT INTO price (product_id,price,how_fresh,price_date,arrived_at)"
            + " VALUES (103,10.0,'EOD',CURRENT_DATE,now())");
    hold(10, 103, 100);

    FundExposure exposure = calculator.forWholeFund(1);

    assertThat(exposure.forCurrency("USD").amountInReportingCurrency()).isEqualTo(1000.0);
  }

  private void hold(int accountId, int productId, double howMany) {
    database.update(
        "INSERT INTO position (account_id,product_id,how_many,what_we_paid,is_a_hedge,"
            + "position_date) VALUES (?,?,?,0,false,CURRENT_DATE)",
        accountId,
        productId,
        howMany);
  }
}
