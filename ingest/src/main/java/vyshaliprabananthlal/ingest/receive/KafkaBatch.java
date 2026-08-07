package vyshaliprabananthlal.ingest.receive;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class KafkaBatch {

  private final JdbcTemplate database;
  private final MeterRegistry meters;

  private final Map<String, Timer> writeTimers = new ConcurrentHashMap<>();
  private final Map<String, Counter> rowCounters = new ConcurrentHashMap<>();
  private final Map<String, Counter> failureCounters = new ConcurrentHashMap<>();

  public KafkaBatch(JdbcTemplate database, MeterRegistry meters) {
    this.database = database;
    this.meters = meters;
  }

  public int writeThenAcknowledge(
      String feed, String statement, List<Object[]> rows, Acknowledgment kafka) {

    int rowsChanged = write(feed, statement, rows);

    everythingLanded(kafka);

    return rowsChanged;
  }

  public int write(String feed, String statement, List<Object[]> rows) {
    Timer.Sample howLongItTook = Timer.start(meters);

    try {
      int rowsChanged = howManyRowsChanged(database.batchUpdate(statement, rows));

      rowCounter(feed).increment(rowsChanged);
      return rowsChanged;

    } catch (RuntimeException theWriteFailed) {
      failureCounter(feed).increment();
      throw theWriteFailed;

    } finally {
      howLongItTook.stop(writeTimer(feed));
    }
  }

  public void everythingLanded(Acknowledgment kafka) {
    kafka.acknowledge();
  }

  private Timer writeTimer(String feed) {
    return writeTimers.computeIfAbsent(
        feed,
        which ->
            Timer.builder("rtat.batch.write")
                .tag("feed", which)
                .publishPercentileHistogram()
                .register(meters));
  }

  private Counter rowCounter(String feed) {
    return rowCounters.computeIfAbsent(
        feed, which -> Counter.builder("rtat.rows.changed").tag("feed", which).register(meters));
  }

  private Counter failureCounter(String feed) {
    return failureCounters.computeIfAbsent(
        feed, which -> Counter.builder("rtat.batch.failed").tag("feed", which).register(meters));
  }

  static int howManyRowsChanged(int[] whatTheDatabaseReported) {
    int total = 0;
    for (int rowsChanged : whatTheDatabaseReported) {
      total = total + Math.max(0, rowsChanged);
    }
    return total;
  }
}
