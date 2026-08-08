package vyshaliprabananthlal.ingest.receive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.Acknowledgment;
import vyshaliprabananthlal.ingest.message.JsonReader;
import vyshaliprabananthlal.ingest.sql.Sql;
import vyshaliprabananthlal.platform.testing.SharedPostgres;

class PositionPriceAndRateListenerTest {

  private static JdbcTemplate database;

  private Acknowledgment kafka;

  @BeforeAll
  static void buildTheSchema() {
    database = SharedPostgres.database();
    SharedPostgres.freshSchema(readFile("db/1-schema.sql"));
  }

  @BeforeEach
  void startWithSomeData() {
    kafka = mock(Acknowledgment.class);

    database.execute(
        "TRUNCATE trade, position, price, fx_rate, account, fund, client, product,"
            + " currency CASCADE");
    database.execute(
        "INSERT INTO currency VALUES ('USD', 'US Dollar', 2), ('JPY', 'Japanese Yen', 0)");
    database.execute(
        "INSERT INTO fx_rate VALUES ('JPY', 'USD', 0.006336, CURRENT_DATE, 'STARTING VALUE')");
    database.execute("INSERT INTO client (name, size, region) VALUES ('Test', 'SMALL', 'US')");
    database.execute(
        "INSERT INTO fund (client_id, name, reporting_currency)"
            + " SELECT client_id, 'Fund', 'USD' FROM client");
    database.execute("INSERT INTO account (fund_id, name) SELECT fund_id, 'Account' FROM fund");
    database.execute(
        "INSERT INTO product (kind, name, currency, identifier)"
            + " VALUES ('SHARES', 'Test Co', 'USD', '000000001')");
    database.execute(
        "INSERT INTO price (product_id, price, how_fresh, price_date, arrived_at)"
            + " SELECT product_id, 100.0, 'DELAYED 20 MINUTES', CURRENT_DATE, now() FROM product");
    database.execute(
        "INSERT INTO position (account_id, product_id, how_many, what_we_paid,"
            + " is_a_hedge, position_date)"
            + " SELECT a.account_id, p.product_id, 1000, 5000, false, CURRENT_DATE"
            + " FROM account a, product p");
  }

  @Test
  @DisplayName("a position message sets the holding to exactly what it says")
  void positionIsSetNotAdded() {
    PositionListener listener =
        new PositionListener(
            new KafkaBatch(database, new SimpleMeterRegistry()), new JsonReader(), new Sql());

    listener.whenPositionsArrive(List.of(positionMessage(750)), kafka);

    assertThat(howManyWeHold()).isEqualTo(750.0);
    verify(kafka).acknowledge();
  }

  @Test
  @DisplayName("the same position message twice leaves the same answer, not double")
  void positionMessagesAreSafeToReplay() {
    PositionListener listener =
        new PositionListener(
            new KafkaBatch(database, new SimpleMeterRegistry()), new JsonReader(), new Sql());

    listener.whenPositionsArrive(List.of(positionMessage(750)), kafka);
    listener.whenPositionsArrive(List.of(positionMessage(750)), kafka);

    assertThat(howManyWeHold()).isEqualTo(750.0);
  }

  @Test
  @DisplayName("a price message updates the price and the arrival time")
  void priceIsUpdated() {
    PriceListener listener =
        new PriceListener(
            new KafkaBatch(database, new SimpleMeterRegistry()), new JsonReader(), new Sql());

    listener.whenPricesArrive(List.of(priceMessage(250.75)), kafka);

    assertThat(currentPrice()).isEqualTo(250.75);
    verify(kafka).acknowledge();
  }

  @Test
  @DisplayName("a rate message updates the rate and marks it as a live tick")
  void rateIsUpdatedAndMarked() {
    RateListener listener =
        new RateListener(
            new KafkaBatch(database, new SimpleMeterRegistry()), new JsonReader(), new Sql());

    listener.whenRatesArrive(List.of(rateMessage("JPY", "USD", 0.0064)), kafka);

    assertThat(currentRate()).isEqualTo(0.0064);
    assertThat(whereTheRateCameFrom()).isEqualTo("LIVE TICK");
    verify(kafka).acknowledge();
  }

  @Test
  @DisplayName("a rate for a pair we do not hold changes nothing and does not fail")
  void unknownRatePairIsIgnored() {
    RateListener listener =
        new RateListener(
            new KafkaBatch(database, new SimpleMeterRegistry()), new JsonReader(), new Sql());

    listener.whenRatesArrive(List.of(rateMessage("ZZZ", "USD", 1.23)), kafka);

    assertThat(currentRate()).isEqualTo(0.006336);
    verify(kafka).acknowledge();
  }

  private String positionMessage(long howMany) {
    return String.format(
        "{\"accountId\":%d,\"productId\":%d,\"howMany\":%d}", accountId(), productId(), howMany);
  }

  private String priceMessage(double price) {
    return String.format(
        "{\"productId\":%d,\"price\":%s,\"howFresh\":\"DELAYED 20 MINUTES\"}", productId(), price);
  }

  private String rateMessage(String from, String to, double rate) {
    return String.format("{\"from\":\"%s\",\"to\":\"%s\",\"rate\":%s}", from, to, rate);
  }

  private int accountId() {
    Integer found = database.queryForObject("SELECT account_id FROM account", Integer.class);
    return found == null ? 0 : found;
  }

  private int productId() {
    Integer found = database.queryForObject("SELECT product_id FROM product", Integer.class);
    return found == null ? 0 : found;
  }

  private double howManyWeHold() {
    Double held = database.queryForObject("SELECT how_many FROM position", Double.class);
    return held == null ? 0 : held;
  }

  private double currentPrice() {
    Double found = database.queryForObject("SELECT price FROM price", Double.class);
    return found == null ? 0 : found;
  }

  private double currentRate() {
    Double found = database.queryForObject("SELECT rate FROM fx_rate", Double.class);
    return found == null ? 0 : found;
  }

  private String whereTheRateCameFrom() {
    String found = database.queryForObject("SELECT where_from FROM fx_rate", String.class);
    return found == null ? "" : found;
  }

  private static String readFile(String path) {
    try (InputStream stream =
        PositionPriceAndRateListenerTest.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) {
        throw new IllegalStateException("not found on the classpath: " + path);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException problem) {
      throw new IllegalStateException("could not read " + path, problem);
    }
  }
}
