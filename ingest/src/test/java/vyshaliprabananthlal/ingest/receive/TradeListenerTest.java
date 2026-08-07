package vyshaliprabananthlal.ingest.receive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.support.Acknowledgment;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import vyshaliprabananthlal.ingest.message.Messages;
import vyshaliprabananthlal.ingest.sql.Sql;

@Testcontainers
class TradeListenerTest {

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.10").withDatabaseName("rtat");

  private static JdbcTemplate database;

  private TradeListener listener;
  private Acknowledgment kafka;

  @BeforeAll
  static void buildTheSchema() {
    DriverManagerDataSource source = new DriverManagerDataSource();
    source.setUrl(POSTGRES.getJdbcUrl());
    source.setUsername(POSTGRES.getUsername());
    source.setPassword(POSTGRES.getPassword());

    database = new JdbcTemplate(source);
    database.execute(readFile("db/1-schema.sql"));
  }

  @BeforeEach
  void startWithOnePosition() {
    listener =
        new TradeListener(
            new KafkaBatch(database, new SimpleMeterRegistry()), new Messages(), new Sql());
    kafka = mock(Acknowledgment.class);

    database.execute("TRUNCATE trade, position, account, fund, client, product, currency CASCADE");
    database.execute("INSERT INTO currency VALUES ('USD', 'US Dollar', 2)");
    database.execute("INSERT INTO client (name, size, region) VALUES ('Test', 'SMALL', 'US')");
    database.execute(
        "INSERT INTO fund (client_id, name, reporting_currency)"
            + " SELECT client_id, 'Fund', 'USD' FROM client");
    database.execute("INSERT INTO account (fund_id, name) SELECT fund_id, 'Account' FROM fund");
    database.execute(
        "INSERT INTO product (kind, name, currency, identifier)"
            + " VALUES ('SHARES', 'Test Co', 'USD', '000000001')");
    database.execute(
        "INSERT INTO position (account_id, product_id, how_many, what_we_paid,"
            + " is_a_hedge, position_date)"
            + " SELECT a.account_id, p.product_id, 1000, 5000, false, CURRENT_DATE"
            + " FROM account a, product p");
  }

  @Test
  @DisplayName("a trade for 200 moves the position from 1000 to 1200")
  void aTradeMovesThePosition() {
    listener.whenTradesArrive(List.of(aTrade(1, 200)), kafka);

    assertThat(howManyWeHold()).isEqualTo(1200.0);
    verify(kafka).acknowledge();
  }

  @Test
  @DisplayName("a sale of 300 takes the position down to 700")
  void aSaleReducesThePosition() {
    listener.whenTradesArrive(List.of(aTrade(1, -300)), kafka);

    assertThat(howManyWeHold()).isEqualTo(700.0);
  }

  @Test
  @DisplayName("the same trade arriving twice moves the position only once")
  void theSameTradeIsNotAppliedTwice() {
    listener.whenTradesArrive(List.of(aTrade(1, 200)), kafka);
    assertThat(howManyWeHold()).isEqualTo(1200.0);

    listener.whenTradesArrive(List.of(aTrade(1, 200)), kafka);

    assertThat(howManyWeHold()).isEqualTo(1200.0);
    assertThat(howManyTradesRecorded()).isEqualTo(1);
  }

  @Test
  @DisplayName("a whole batch replayed moves nothing a second time")
  void awholeBatchCanBeReplayed() {
    List<String> batch = List.of(aTrade(1, 100), aTrade(2, 200), aTrade(3, -50));

    listener.whenTradesArrive(batch, kafka);
    assertThat(howManyWeHold()).isEqualTo(1250.0);

    listener.whenTradesArrive(batch, kafka);

    assertThat(howManyWeHold()).isEqualTo(1250.0);
    assertThat(howManyTradesRecorded()).isEqualTo(3);
    verify(kafka, times(2)).acknowledge();
  }

  @Test
  @DisplayName("different trades all apply")
  void differentTradesAllApply() {
    listener.whenTradesArrive(List.of(aTrade(1, 100), aTrade(2, 100), aTrade(3, 100)), kafka);

    assertThat(howManyWeHold()).isEqualTo(1300.0);
    assertThat(howManyTradesRecorded()).isEqualTo(3);
  }

  private String aTrade(long tradeNumber, long howMany) {
    Integer account = database.queryForObject("SELECT account_id FROM account", Integer.class);
    Integer product = database.queryForObject("SELECT product_id FROM product", Integer.class);

    return String.format(
        "{\"tradeId\":%d,\"accountId\":%d,\"productId\":%d,\"howMany\":%d,"
            + "\"price\":10.5,\"happenedAt\":\"2026-08-06T14:32:07Z\","
            + "\"cameFrom\":\"AUTOMATIC FEED\"}",
        tradeNumber, account, product, howMany);
  }

  private double howManyWeHold() {
    Double held = database.queryForObject("SELECT how_many FROM position", Double.class);
    return held == null ? 0 : held;
  }

  private int howManyTradesRecorded() {
    Integer counted = database.queryForObject("SELECT count(*) FROM trade", Integer.class);
    return counted == null ? 0 : counted;
  }

  private static String readFile(String path) {
    try (InputStream stream = TradeListenerTest.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) {
        throw new IllegalStateException("not found on the classpath: " + path);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException problem) {
      throw new IllegalStateException("could not read " + path, problem);
    }
  }
}
