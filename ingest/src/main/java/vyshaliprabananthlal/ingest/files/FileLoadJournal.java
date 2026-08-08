package vyshaliprabananthlal.ingest.files;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vyshaliprabananthlal.platform.sql.SqlStatements;

@Component
public class FileLoadJournal {

    private final JdbcTemplate database;
    private final SqlStatements statements;

    public FileLoadJournal(JdbcTemplate database, SqlStatements statements) {
        this.database = database;
        this.statements = statements;
    }

    public Optional<Integer> findByFingerprint(String fingerprint) {
        List<Integer> found = database.query(
                statements.statement("find-load-by-fingerprint"), (row, number) -> row.getInt(1), fingerprint);

        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    public int startLoad(String fileName, String fingerprint, String custodian, String arrivedHow, int rowsInFile) {

        Integer loadId = database.queryForObject(
                statements.statement("insert-file-load"),
                Integer.class,
                fileName,
                fingerprint,
                custodian,
                arrivedHow,
                rowsInFile,
                now());

        if (loadId == null) {
            throw new IllegalStateException("the database did not give back a file load id");
        }
        return loadId;
    }

    public void finishLoad(int loadId, int rowsLoaded, int rowsRejected) {
        database.update(statements.statement("update-file-load-totals"), rowsLoaded, rowsRejected, now(), loadId);
    }

    public void recordBadLine(int loadId, int lineNumber, String line, String reason) {
        database.update(statements.statement("insert-load-problem"), loadId, lineNumber, line, reason);
    }

    public List<String> problemsFor(int loadId) {
        return database.query(
                statements.statement("select-load-problems"),
                (row, number) -> "line " + row.getInt(1) + ": " + row.getString(2),
                loadId);
    }

    private static Timestamp now() {
        return Timestamp.from(Instant.now());
    }
}
