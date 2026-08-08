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

class HedgeFillListenerTest {

  private static JdbcTemplate database;

  private HedgeFillListener listener;
  private Acknowledgment kafka;

  @BeforeAll
  static void buildTheSchema() {
    database = SharedPostgres.database();
    SharedPostgres.freshSchema(readFile("db/1-schema.sql"));
  }

  @BeforeEach
  void startWithOneHedgeWaitingToBeFilled() {
    listener =
        new HedgeFillListener(
            database,
            new KafkaBatch(database, new SimpleMeterRegistry()),
            new JsonReader(),
            new Sql());
    kafka = mock(Acknowledgment.class);

    database.execute("TRUNCATE hedge_fill, hedge, fund, client, currency CASCADE");
    database.execute("INSERT INTO currency VALUES ('EUR', 'Euro', 2)");
    database.execute("INSERT INTO client (name, size, region) VALUES ('Test', 'LARGE', 'EUROPE')");
    database.execute(
        "INSERT INTO fund (client_id, name, reporting_currency)"
            + " SELECT client_id, 'Fund', 'EUR' FROM client");
    database.execute(
        "INSERT INTO hedge (hedge_id, fund_id, currency, hedge_date, exposure_amount,"
            + " suggested_amount, chosen_amount, instrument, settles_on, status, external_reference)"
            + " SELECT 500, fund_id, 'EUR', CURRENT_DATE, -9000000, 9000000, 9000000,"
            + " 'FORWARD', CURRENT_DATE + 30, 'SENT', 'FXM-77120' FROM fund");
  }

  @Test
  @DisplayName("a fill that covers the whole hedge marks it FILLED")
  void aFullFillCompletesTheHedge() {
    listener.whenFillsArrive(List.of(aFill(1, 9000000, 1.1540)), kafka);

    assertThat(howManyFills()).isEqualTo(1);
    assertThat(hedgeStatus()).isEqualTo("FILLED");
    verify(kafka).acknowledge();
  }

  @Test
  @DisplayName("a fill that covers only part of it leaves the hedge PARTIALLY FILLED")
  void aPartialFillLeavesItOpen() {
    listener.whenFillsArrive(List.of(aFill(1, 6000000, 1.1540)), kafka);

    assertThat(hedgeStatus()).isEqualTo("PARTIALLY FILLED");
  }

  @Test
  @DisplayName("two fills that add up to the whole amount finish the hedge")
  void twoFillsTogetherCompleteIt() {
    listener.whenFillsArrive(List.of(aFill(1, 6000000, 1.1540)), kafka);
    assertThat(hedgeStatus()).isEqualTo("PARTIALLY FILLED");

    listener.whenFillsArrive(List.of(aFill(2, 3000000, 1.1538)), kafka);

    assertThat(howManyFills()).isEqualTo(2);
    assertThat(hedgeStatus()).isEqualTo("FILLED");
  }

  @Test
  @DisplayName("the same fill arriving twice is recorded once")
  void theSameFillIsNotRecordedTwice() {
    listener.whenFillsArrive(List.of(aFill(1, 6000000, 1.1540)), kafka);
    listener.whenFillsArrive(List.of(aFill(1, 6000000, 1.1540)), kafka);

    assertThat(howManyFills()).isEqualTo(1);
    assertThat(totalFilled()).isEqualTo(6000000.0);
  }

  @Test
  @DisplayName("a whole batch of fills replayed does not double the filled amount")
  void aBatchCanBeReplayed() {
    List<String> batch = List.of(aFill(1, 4000000, 1.1540), aFill(2, 5000000, 1.1538));

    listener.whenFillsArrive(batch, kafka);
    assertThat(totalFilled()).isEqualTo(9000000.0);

    listener.whenFillsArrive(batch, kafka);

    assertThat(howManyFills()).isEqualTo(2);
    assertThat(totalFilled()).isEqualTo(9000000.0);
  }

  @Test
  @DisplayName("each fill keeps the rate it was actually done at")
  void eachFillKeepsItsOwnRate() {
    listener.whenFillsArrive(List.of(aFill(1, 6000000, 1.1540), aFill(2, 3000000, 1.1538)), kafka);

    List<Double> rates =
        database.query(
            "SELECT fill_rate FROM hedge_fill ORDER BY fill_id", (row, number) -> row.getDouble(1));

    assertThat(rates).containsExactly(1.1540, 1.1538);
  }

  private String aFill(long fillId, long amount, double rate) {
    return String.format(
        "{\"fillId\":%d,\"hedgeId\":500,\"amountFilled\":%d,\"rate\":%s,"
            + "\"filledAt\":\"2026-08-06T14:32:11Z\",\"theirReference\":\"FXM-77120-%d\"}",
        fillId, amount, rate, fillId);
  }

  private int howManyFills() {
    Integer counted = database.queryForObject("SELECT count(*) FROM hedge_fill", Integer.class);
    return counted == null ? 0 : counted;
  }

  private double totalFilled() {
    Double total =
        database.queryForObject(
            "SELECT coalesce(sum(amount_filled), 0) FROM hedge_fill", Double.class);
    return total == null ? 0 : total;
  }

  private String hedgeStatus() {
    String status = database.queryForObject("SELECT status FROM hedge", String.class);
    return status == null ? "" : status;
  }

  private static String readFile(String path) {
    try (InputStream stream =
        HedgeFillListenerTest.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) {
        throw new IllegalStateException("not found on the classpath: " + path);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException problem) {
      throw new IllegalStateException("could not read " + path, problem);
    }
  }
}
