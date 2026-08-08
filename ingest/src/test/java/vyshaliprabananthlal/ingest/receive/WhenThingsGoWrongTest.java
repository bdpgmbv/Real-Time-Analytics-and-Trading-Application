package vyshaliprabananthlal.ingest.receive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.support.Acknowledgment;
import vyshaliprabananthlal.ingest.message.JsonReader;
import vyshaliprabananthlal.ingest.sql.Sql;
import vyshaliprabananthlal.platform.testing.SharedPostgres;

class WhenThingsGoWrongTest {

  private static JdbcTemplate database;

  private Acknowledgment kafka;

  @BeforeAll
  static void buildTheSchema() {
    database = SharedPostgres.database();
    SharedPostgres.freshSchema(readFile("db/1-schema.sql"));
  }

  @BeforeEach
  void setUp() {
    kafka = mock(Acknowledgment.class);
  }

  @Test
  @DisplayName("a message that is not JSON at all stops the batch and does not acknowledge")
  void rubbishOnTheTopicDoesNotAcknowledge() {
    PositionListener listener = positionListener();

    assertThatThrownBy(() -> listener.whenPositionsArrive(List.of("this is not json"), kafka))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("could not read message");

    verify(kafka, never()).acknowledge();
  }

  @Test
  @DisplayName("a message missing every field writes nothing rather than writing nonsense")
  void anEmptyMessageWritesNothing() {
    PositionListener listener = positionListener();

    listener.whenPositionsArrive(List.of("{}"), kafka);

    assertThat(howManyPositions()).isZero();
    verify(kafka).acknowledge();
  }

  @Test
  @DisplayName("one bad message in a good batch stops the whole batch, so none of it is lost")
  void oneBadMessagePoisonsTheBatchOnPurpose() {
    PositionListener listener = positionListener();

    assertThatThrownBy(
            () ->
                listener.whenPositionsArrive(
                    List.of(
                        "{\"accountId\":1,\"productId\":1,\"howMany\":10}",
                        "NOT JSON",
                        "{\"accountId\":2,\"productId\":2,\"howMany\":20}"),
                    kafka))
        .isInstanceOf(IllegalStateException.class);

    verify(kafka, never()).acknowledge();
  }

  @Test
  @DisplayName("a database that has gone away leaves Kafka un-acknowledged")
  void aDatabaseThatWentAwayDoesNotAcknowledge() {
    DriverManagerDataSource pointingNowhere = new DriverManagerDataSource();
    pointingNowhere.setUrl("jdbc:postgresql://localhost:1/nothing-here");
    pointingNowhere.setUsername("nobody");
    pointingNowhere.setPassword("nothing");

    KafkaBatch batch = new KafkaBatch(new JdbcTemplate(pointingNowhere), new SimpleMeterRegistry());

    assertThatThrownBy(
            () ->
                batch.writeThenAcknowledge(
                    "position",
                    "UPDATE position SET quantity = ? WHERE account_id = ? AND product_id = ?",
                    List.<Object[]>of(new Object[] {1.0, 1, 1}),
                    kafka))
        .isInstanceOf(DataAccessResourceFailureException.class);

    verify(kafka, never()).acknowledge();
  }

  @Test
  @DisplayName("an empty poll acknowledges and moves on rather than failing")
  void anEmptyPollIsFine() {
    PositionListener listener = positionListener();

    listener.whenPositionsArrive(List.of(), kafka);

    verify(kafka).acknowledge();
  }

  @Test
  @DisplayName("a number arriving as text is refused, not silently read as zero")
  void aNumberSentAsTextIsNotSilentlyZero() {
    PositionListener listener = positionListener();

    listener.whenPositionsArrive(
        List.of("{\"accountId\":1,\"productId\":1,\"howMany\":\"not a number\"}"), kafka);

    assertThat(howManyPositions()).isZero();
  }

  @Test
  @DisplayName("a failed write is counted, so the alert can see it")
  void aFailedWriteIsCounted() {
    SimpleMeterRegistry meters = new SimpleMeterRegistry();
    DriverManagerDataSource pointingNowhere = new DriverManagerDataSource();
    pointingNowhere.setUrl("jdbc:postgresql://localhost:1/nothing-here");

    KafkaBatch batch = new KafkaBatch(new JdbcTemplate(pointingNowhere), meters);

    try {
      batch.write(
          "position", "UPDATE position SET quantity = ?", List.<Object[]>of(new Object[] {1}));
    } catch (RuntimeException expected) {
      assertThat(expected).isNotNull();
    }

    assertThat(meters.get("rtat.batch.failed").tag("feed", "position").counter().count())
        .isEqualTo(1.0);
  }

  private PositionListener positionListener() {
    database.execute("TRUNCATE position");
    return new PositionListener(
        new KafkaBatch(database, new SimpleMeterRegistry()), new JsonReader(), new Sql());
  }

  private int howManyPositions() {
    Integer counted =
        database.queryForObject("SELECT count(*) FROM position WHERE quantity <> 0", Integer.class);
    return counted == null ? 0 : counted;
  }

  private static String readFile(String path) {
    try (java.io.InputStream stream =
        WhenThingsGoWrongTest.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) {
        throw new IllegalStateException("not found on the classpath: " + path);
      }
      return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    } catch (java.io.IOException problem) {
      throw new IllegalStateException("could not read " + path, problem);
    }
  }
}
