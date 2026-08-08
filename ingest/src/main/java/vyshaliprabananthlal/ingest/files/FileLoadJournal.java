package vyshaliprabananthlal.ingest.files;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vyshaliprabananthlal.ingest.sql.Sql;

@Component
public class FileLoadJournal {

  private final JdbcTemplate database;
  private final Sql sql;

  public FileLoadJournal(JdbcTemplate database, Sql sql) {
    this.database = database;
    this.sql = sql;
  }

  public Optional<Integer> findByFingerprint(String fingerprint) {
    List<Integer> found =
        database.query(
            sql.statement("find-load-by-fingerprint"), (row, number) -> row.getInt(1), fingerprint);

    return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
  }

  public int startLoad(
      String fileName, String fingerprint, String custodian, String arrivedHow, int rowsInFile) {

    Integer fileLoadId =
        database.queryForObject(
            sql.statement("start-file-load"),
            Integer.class,
            fileName,
            fingerprint,
            custodian,
            arrivedHow,
            rowsInFile,
            currentPrice());

    if (fileLoadId == null) {
      throw new IllegalStateException("the database did not give back a file load id");
    }
    return fileLoadId;
  }

  public void finishLoad(int fileLoadId, int rowsLoaded, int rowsRejected) {
    database.update(
        sql.statement("finish-file-load"), rowsLoaded, rowsRejected, currentPrice(), fileLoadId);
  }

  public void recordBadLine(int fileLoadId, int lineNumber, String line, String reason) {
    database.update(sql.statement("record-bad-line"), fileLoadId, lineNumber, line, reason);
  }

  public List<String> problemsFor(int fileLoadId) {
    return database.query(
        sql.statement("list-load-problems"),
        (row, number) -> "line " + row.getInt(1) + ": " + row.getString(2),
        fileLoadId);
  }

  private static Timestamp currentPrice() {
    return Timestamp.from(Instant.now());
  }
}
