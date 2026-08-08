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
    private final String findByFingerprintSql;
    private final String insertFileLoad;
    private final String updateFileLoadTotals;
    private final String insertLoadProblem;
    private final String selectLoadProblems;

    public FileLoadJournal(JdbcTemplate database, SqlStatements statements) {
        this.database = database;
        this.findByFingerprintSql = statements.statement("find-load-by-fingerprint");
        this.insertFileLoad = statements.statement("insert-file-load");
        this.updateFileLoadTotals = statements.statement("update-file-load-totals");
        this.insertLoadProblem = statements.statement("insert-load-problem");
        this.selectLoadProblems = statements.statement("select-load-problems");
    }

    public Optional<Integer> findByFingerprint(String fingerprint) {
        List<Integer> found = database.query(findByFingerprintSql, (row, number) -> row.getInt(1), fingerprint);

        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    public int startLoad(String fileName, String fingerprint, String custodian, String arrivedHow, int rowsInFile) {

        Integer loadId = database.queryForObject(
                insertFileLoad, Integer.class, fileName, fingerprint, custodian, arrivedHow, rowsInFile, now());

        if (loadId == null) {
            throw new IllegalStateException("the database did not give back a file load id");
        }
        return loadId;
    }

    public void finishLoad(int loadId, int rowsLoaded, int rowsRejected) {
        database.update(updateFileLoadTotals, rowsLoaded, rowsRejected, now(), loadId);
    }

    public void recordBadLine(int loadId, int lineNumber, String line, String reason) {
        database.update(insertLoadProblem, loadId, lineNumber, line, reason);
    }

    public List<String> problemsFor(int loadId) {
        return database.query(
                selectLoadProblems, (row, number) -> "line " + row.getInt(1) + ": " + row.getString(2), loadId);
    }

    private static Timestamp now() {
        return Timestamp.from(Instant.now());
    }
}
