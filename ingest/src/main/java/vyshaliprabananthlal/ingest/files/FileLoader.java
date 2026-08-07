package vyshaliprabananthlal.ingest.files;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vyshaliprabananthlal.ingest.format.BadLine;
import vyshaliprabananthlal.ingest.format.CustodianFormat;
import vyshaliprabananthlal.ingest.format.PositionRow;
import vyshaliprabananthlal.ingest.sql.Sql;

@Service
public class FileLoader {

  private static final Logger LOG = LoggerFactory.getLogger(FileLoader.class);

  private final JdbcTemplate database;
  private final LoadBook book;
  private final List<CustodianFormat> knownFormats;
  private final String savePosition;
  private final Counter rowsLoadedCounter;
  private final Counter rowsRejectedCounter;
  private final Counter filesSkippedCounter;

  public FileLoader(
      JdbcTemplate database,
      LoadBook book,
      List<CustodianFormat> knownFormats,
      Sql sql,
      MeterRegistry meters) {

    this.database = database;
    this.book = book;
    this.knownFormats = knownFormats;
    this.savePosition = sql.statement("save-position-from-file");
    this.rowsLoadedCounter = meters.counter("rtat.file.rows.loaded");
    this.rowsRejectedCounter = meters.counter("rtat.file.rows.rejected");
    this.filesSkippedCounter = meters.counter("rtat.file.skipped");
  }

  public LoadResult load(String fileName, String contents, String arrivedHow) {
    String fingerprint = Fingerprint.of(contents);

    Optional<Integer> alreadyLoaded = book.findLoadOf(fingerprint);
    if (alreadyLoaded.isPresent()) {
      LOG.info("{} has been loaded before, skipping it", fileName);
      filesSkippedCounter.increment();
      return LoadResult.alreadySeen(alreadyLoaded.get());
    }

    List<String> lines = contents.lines().toList();
    if (lines.isEmpty()) {
      throw new BadLine("the file is empty");
    }

    CustodianFormat format = whichFormatIsThis(lines.get(0));
    int rowsInFile = lines.size() - 1;
    int fileLoadId =
        book.startALoad(fileName, fingerprint, format.custodianName(), arrivedHow, rowsInFile);

    Tally tally = readEveryLine(lines, format, fileLoadId);

    book.finishALoad(fileLoadId, tally.loaded(), tally.rejected());
    rowsLoadedCounter.increment(tally.loaded());
    rowsRejectedCounter.increment(tally.rejected());
    LOG.info("{}: {} rows loaded, {} rejected", fileName, tally.loaded(), tally.rejected());

    return new LoadResult(
        fileLoadId, format.custodianName(), rowsInFile, tally.loaded(), tally.rejected(), false);
  }

  public List<String> problemsFrom(int fileLoadId) {
    return book.problemsFrom(fileLoadId);
  }

  private Tally readEveryLine(List<String> lines, CustodianFormat format, int fileLoadId) {
    int loaded = 0;
    int rejected = 0;

    for (int lineNumber = 1; lineNumber < lines.size(); lineNumber++) {
      String line = lines.get(lineNumber);
      if (line.isBlank()) {
        continue;
      }

      Optional<String> whatIsWrong = savePositionFrom(line, format);

      if (whatIsWrong.isEmpty()) {
        loaded = loaded + 1;
      } else {
        book.recordABadLine(fileLoadId, lineNumber + 1, line, whatIsWrong.get());
        rejected = rejected + 1;
      }
    }
    return new Tally(loaded, rejected);
  }

  private Optional<String> savePositionFrom(String line, CustodianFormat format) {
    try {
      PositionRow row = format.readOneLine(line);

      int rowsChanged =
          database.update(
              savePosition, row.howMany(), row.whatWePaid(), row.accountName(), row.identifier());

      return rowsChanged == 0 ? Optional.of("no such account or security") : Optional.empty();

    } catch (BadLine whatIsWrong) {
      return Optional.of(whatIsWrong.whatIsWrong());
    }
  }

  private CustodianFormat whichFormatIsThis(String headingLine) {
    for (CustodianFormat format : knownFormats) {
      if (format.looksLikeMine(headingLine)) {
        return format;
      }
    }
    throw new BadLine("no custodian format matches this heading: " + headingLine);
  }

  private record Tally(int loaded, int rejected) {}
}
