package vyshaliprabananthlal.ingest.files;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vyshaliprabananthlal.ingest.format.CustodianFormat;
import vyshaliprabananthlal.ingest.sql.Sql;

@Service
public class FileLoader {

    private static final Logger LOG = LoggerFactory.getLogger(FileLoader.class);

    private final JdbcTemplate database;
    private final FileLoadJournal journal;
    private final List<CustodianFormat> knownFormats;
    private final String savePositionSql;
    private final FileCounters counters;

    public FileLoader(
            JdbcTemplate database,
            FileLoadJournal journal,
            List<CustodianFormat> knownFormats,
            Sql sql,
            MeterRegistry meters) {
        this.database = database;
        this.journal = journal;
        this.knownFormats = knownFormats;
        this.savePositionSql = sql.statement("upsert-position-from-file");
        this.counters = new FileCounters(meters);
    }

    /** Loads one custodian file. Good rows land, bad rows are recorded and reported back. */
    public LoadResult load(String fileName, String contents, String howItArrived) {
        String fingerprint = fingerprintOf(contents);

        Optional<Integer> loadedBefore = journal.findByFingerprint(fingerprint);
        if (loadedBefore.isPresent()) {
            LOG.info("{} has been loaded before, skipping it", fileName);
            counters.filesSkipped.increment();
            return LoadResult.alreadySeen(loadedBefore.get());
        }

        List<String> lines = contents.lines().toList();
        if (lines.isEmpty()) {
            throw new CustodianFormat.BadLine("the file is empty");
        }

        CustodianFormat format = formatFor(lines.get(0));
        int rowsInFile = lines.size() - 1;
        int loadId = journal.startLoad(fileName, fingerprint, format.custodianName(), howItArrived, rowsInFile);

        int loaded = 0;
        int rejected = 0;

        for (int lineNumber = 1; lineNumber < lines.size(); lineNumber++) {
            String line = lines.get(lineNumber);
            if (line.isBlank()) {
                continue;
            }

            Optional<String> whatWasWrong = savePositionFrom(line, format);

            if (whatWasWrong.isEmpty()) {
                loaded++;
            } else {
                journal.recordBadLine(loadId, lineNumber + 1, line, whatWasWrong.get());
                rejected++;
            }
        }

        journal.finishLoad(loadId, loaded, rejected);
        counters.rowsLoaded.increment(loaded);
        counters.rowsRejected.increment(rejected);
        LOG.info("{}: {} rows loaded, {} rejected", fileName, loaded, rejected);

        return new LoadResult(loadId, format.custodianName(), rowsInFile, loaded, rejected, false);
    }

    public List<String> problemsFor(int loadId) {
        return journal.problemsFor(loadId);
    }

    /** Empty means the row saved. Otherwise it says what was wrong with it. */
    private Optional<String> savePositionFrom(String line, CustodianFormat format) {
        try {
            CustodianFormat.PositionRow row = format.readOneLine(line);

            int rowsChanged =
                    database.update(savePositionSql, row.quantity(), row.cost(), row.accountName(), row.identifier());

            return rowsChanged == 0 ? Optional.of("no such account or security") : Optional.empty();

        } catch (CustodianFormat.BadLine whatWasWrong) {
            return Optional.of(whatWasWrong.reason());
        }
    }

    private CustodianFormat formatFor(String headingLine) {
        for (CustodianFormat format : knownFormats) {
            if (format.matches(headingLine)) {
                return format;
            }
        }
        throw new CustodianFormat.BadLine("no custodian format matches this heading: " + headingLine);
    }

    /** A file is known by what is in it, not by what it is called. */
    private static String fingerprintOf(String contents) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(contents.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("this machine has no SHA-256", impossible);
        }
    }

    public record LoadResult(
            int loadId, String custodian, int rowsInFile, int rowsLoaded, int rowsRejected, boolean wasAlreadySeen) {

        static LoadResult alreadySeen(int loadId) {
            return new LoadResult(loadId, "", 0, 0, 0, true);
        }
    }

    private static final class FileCounters {
        final Counter rowsLoaded;
        final Counter rowsRejected;
        final Counter filesSkipped;

        FileCounters(MeterRegistry meters) {
            this.rowsLoaded = meters.counter("rtat.file.rows.loaded");
            this.rowsRejected = meters.counter("rtat.file.rows.rejected");
            this.filesSkipped = meters.counter("rtat.file.skipped");
        }
    }
}
