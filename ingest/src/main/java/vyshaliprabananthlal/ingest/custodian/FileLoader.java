package vyshaliprabananthlal.ingest.custodian;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class FileLoader {

  private static final Logger LOG = LoggerFactory.getLogger(FileLoader.class);

  private static final String HAVE_WE_SEEN_THIS_FILE =
      "SELECT file_load_id FROM file_load WHERE fingerprint = ?";

  private static final String START_A_LOAD =
      "INSERT INTO file_load"
          + " (file_name, fingerprint, custodian, arrived_how, rows_in_file,"
          + "  rows_loaded, rows_rejected, started_at)"
          + " VALUES (?, ?, ?, ?, ?, 0, 0, ?) RETURNING file_load_id";

  private static final String FINISH_A_LOAD =
      "UPDATE file_load SET rows_loaded = ?, rows_rejected = ?, finished_at = ?"
          + " WHERE file_load_id = ?";

  private static final String RECORD_A_BAD_LINE =
      "INSERT INTO file_row_problem (file_load_id, line_number, the_line, what_is_wrong)"
          + " VALUES (?, ?, ?, ?)";

  private static final String SAVE_THE_POSITION =
      "INSERT INTO position"
          + " (account_id, product_id, how_many, what_we_paid, is_a_hedge, position_date)"
          + " SELECT a.account_id, p.product_id, ?, ?, false, CURRENT_DATE"
          + "   FROM account a, product p"
          + "  WHERE a.name = ? AND p.identifier = ?"
          + " ON CONFLICT (account_id, product_id)"
          + " DO UPDATE SET how_many = EXCLUDED.how_many, what_we_paid = EXCLUDED.what_we_paid";

  private final JdbcTemplate database;
  private final List<CustodianFormat> knownFormats;

  public FileLoader(JdbcTemplate database, List<CustodianFormat> knownFormats) {
    this.database = database;
    this.knownFormats = knownFormats;
  }

  public LoadResult load(String fileName, String contents, String arrivedHow) {
    String fingerprint = fingerprintOf(contents);

    Optional<Integer> alreadyLoaded = findExistingLoad(fingerprint);
    if (alreadyLoaded.isPresent()) {
      LOG.info("{} has been loaded before, skipping it", fileName);
      return LoadResult.alreadySeen(alreadyLoaded.get());
    }

    List<String> lines = contents.lines().toList();
    if (lines.isEmpty()) {
      throw new BadLine("the file is empty");
    }

    CustodianFormat format = whichFormatIsThis(lines.get(0));
    int rowsInFile = lines.size() - 1;

    int fileLoadId =
        startALoad(fileName, fingerprint, format.custodianName(), arrivedHow, rowsInFile);

    int loaded = 0;
    int rejected = 0;

    for (int lineNumber = 1; lineNumber < lines.size(); lineNumber++) {
      String line = lines.get(lineNumber);
      if (line.isBlank()) {
        continue;
      }

      try {
        PositionRow row = format.readOneLine(line);
        int rowsChanged = savePosition(row);

        if (rowsChanged == 0) {
          recordABadLine(fileLoadId, lineNumber + 1, line, "no such account or security");
          rejected = rejected + 1;
        } else {
          loaded = loaded + 1;
        }
      } catch (BadLine whatIsWrong) {
        recordABadLine(fileLoadId, lineNumber + 1, line, whatIsWrong.whatIsWrong());
        rejected = rejected + 1;
      }
    }

    database.update(FINISH_A_LOAD, loaded, rejected, rightNow(), fileLoadId);
    LOG.info("{}: {} rows loaded, {} rejected", fileName, loaded, rejected);

    return new LoadResult(fileLoadId, format.custodianName(), rowsInFile, loaded, rejected, false);
  }

  private Optional<Integer> findExistingLoad(String fingerprint) {
    List<Integer> found =
        database.query(HAVE_WE_SEEN_THIS_FILE, (row, number) -> row.getInt(1), fingerprint);
    return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
  }

  private CustodianFormat whichFormatIsThis(String headingLine) {
    for (CustodianFormat format : knownFormats) {
      if (format.looksLikeMine(headingLine)) {
        return format;
      }
    }
    throw new BadLine("no custodian format matches this heading: " + headingLine);
  }

  private int startALoad(
      String fileName, String fingerprint, String custodian, String arrivedHow, int rowsInFile) {

    Integer fileLoadId =
        database.queryForObject(
            START_A_LOAD,
            Integer.class,
            fileName,
            fingerprint,
            custodian,
            arrivedHow,
            rowsInFile,
            rightNow());

    if (fileLoadId == null) {
      throw new IllegalStateException("the database did not give back a file load id");
    }
    return fileLoadId;
  }

  private int savePosition(PositionRow row) {
    return database.update(
        SAVE_THE_POSITION, row.howMany(), row.whatWePaid(), row.accountName(), row.identifier());
  }

  private void recordABadLine(int fileLoadId, int lineNumber, String line, String whatIsWrong) {
    database.update(RECORD_A_BAD_LINE, fileLoadId, lineNumber, line, whatIsWrong);
  }

  private static Timestamp rightNow() {
    return Timestamp.from(Instant.now());
  }

  static String fingerprintOf(String contents) {
    try {
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      byte[] digest = sha256.digest(contents.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("this machine has no SHA-256", impossible);
    }
  }

  public List<String> problemsFrom(int fileLoadId) {
    return database.query(
        "SELECT line_number, what_is_wrong FROM file_row_problem"
            + " WHERE file_load_id = ? ORDER BY line_number",
        (row, number) -> "line " + row.getInt(1) + ": " + row.getString(2),
        fileLoadId);
  }

  public record LoadResult(
      int fileLoadId,
      String custodian,
      int rowsInFile,
      int rowsLoaded,
      int rowsRejected,
      boolean wasAlreadySeen) {

    static LoadResult alreadySeen(int fileLoadId) {
      return new LoadResult(fileLoadId, "", 0, 0, 0, true);
    }
  }
}
