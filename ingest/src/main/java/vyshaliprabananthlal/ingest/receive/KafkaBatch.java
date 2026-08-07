package vyshaliprabananthlal.ingest.receive;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class KafkaBatch {

  private final JdbcTemplate database;

  public KafkaBatch(JdbcTemplate database) {
    this.database = database;
  }

  public int writeThenAcknowledge(String statement, List<Object[]> rows, Acknowledgment kafka) {
    int rowsChanged = write(statement, rows);

    everythingLanded(kafka);

    return rowsChanged;
  }

  public int write(String statement, List<Object[]> rows) {
    return howManyRowsChanged(database.batchUpdate(statement, rows));
  }

  public void everythingLanded(Acknowledgment kafka) {
    kafka.acknowledge();
  }

  static int howManyRowsChanged(int[] whatTheDatabaseReported) {
    int total = 0;
    for (int rowsChanged : whatTheDatabaseReported) {
      total = total + Math.max(0, rowsChanged);
    }
    return total;
  }
}
