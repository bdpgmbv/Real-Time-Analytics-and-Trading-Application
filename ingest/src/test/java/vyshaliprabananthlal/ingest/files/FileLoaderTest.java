package vyshaliprabananthlal.ingest.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import vyshaliprabananthlal.ingest.format.BadLine;
import vyshaliprabananthlal.ingest.format.CommaFormat;
import vyshaliprabananthlal.ingest.format.PipeFormat;
import vyshaliprabananthlal.ingest.sql.Sql;

@Testcontainers
class FileLoaderTest {

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.10").withDatabaseName("rtat");

  private static JdbcTemplate database;

  private FileLoader loader;

  @BeforeAll
  static void buildTheSchema() {
    DriverManagerDataSource source = new DriverManagerDataSource();
    source.setUrl(POSTGRES.getJdbcUrl());
    source.setUsername(POSTGRES.getUsername());
    source.setPassword(POSTGRES.getPassword());

    database = new JdbcTemplate(source);
    database.execute(readFile("db/1-schema.sql"));
    database.execute("ALTER TABLE position ADD PRIMARY KEY (account_id, product_id)");
  }

  @BeforeEach
  void startWithAnAccountAndTwoSecurities() {
    loader =
        new FileLoader(
            database,
            new LoadBook(database, new Sql()),
            List.of(new CommaFormat(), new PipeFormat()),
            new Sql());

    database.execute(
        "TRUNCATE file_row_problem, file_load, position, account, fund, client, product,"
            + " currency CASCADE");
    database.execute("INSERT INTO currency VALUES ('USD', 'US Dollar', 2)");
    database.execute("INSERT INTO client (name, size, region) VALUES ('Test', 'SMALL', 'US')");
    database.execute(
        "INSERT INTO fund (client_id, name, reporting_currency)"
            + " SELECT client_id, 'Fund', 'USD' FROM client");
    database.execute("INSERT INTO account (fund_id, name) SELECT fund_id, 'ACCOUNT-1' FROM fund");
    database.execute(
        "INSERT INTO product (kind, name, currency, identifier) VALUES"
            + " ('SHARES', 'Alpha Co', 'USD', '000000001'),"
            + " ('SHARES', 'Beta Co',  'USD', '000000002')");
  }

  @Test
  @DisplayName("a comma file from one custodian loads its rows")
  void commaFileLoads() {
    LoadResult result = loader.load("northgate.csv", commaFile(), "SFTP OVERNIGHT");

    assertThat(result.custodian()).isEqualTo("Northgate Trust");
    assertThat(result.rowsLoaded()).isEqualTo(2);
    assertThat(result.rowsRejected()).isZero();
    assertThat(howManyWeHold("000000001")).isEqualTo(500.0);
    assertThat(howManyWeHold("000000002")).isEqualTo(250.0);
  }

  @Test
  @DisplayName("a pipe file from a different custodian loads the same way")
  void pipeFileLoads() {
    LoadResult result = loader.load("halloway.txt", pipeFile(), "SFTP INTRADAY");

    assertThat(result.custodian()).isEqualTo("Halloway Bank");
    assertThat(result.rowsLoaded()).isEqualTo(2);
    assertThat(howManyWeHold("000000001")).isEqualTo(900.0);
    assertThat(howManyWeHold("000000002")).isEqualTo(100.0);
  }

  @Test
  @DisplayName("the pipe format puts cost and quantity in the other order, and still lands right")
  void pipeFormatColumnOrderIsRespected() {
    loader.load("halloway.txt", pipeFile(), "SFTP INTRADAY");

    Double whatWePaid =
        database.queryForObject(
            "SELECT what_we_paid FROM position p JOIN product r ON r.product_id = p.product_id"
                + " WHERE r.identifier = '000000001'",
            Double.class);

    assertThat(whatWePaid).isEqualTo(45000.0);
  }

  @Test
  @DisplayName("the same file sent twice is loaded once")
  void theSameFileIsNotLoadedTwice() {
    LoadResult first = loader.load("northgate.csv", commaFile(), "SFTP OVERNIGHT");
    LoadResult second = loader.load("northgate.csv", commaFile(), "SFTP OVERNIGHT");

    assertThat(first.wasAlreadySeen()).isFalse();
    assertThat(second.wasAlreadySeen()).isTrue();
    assertThat(second.fileLoadId()).isEqualTo(first.fileLoadId());
    assertThat(howManyFilesLoaded()).isEqualTo(1);
    assertThat(howManyWeHold("000000001")).isEqualTo(500.0);
  }

  @Test
  @DisplayName("the same file under a different name is still recognised as the same file")
  void renamingAFileDoesNotFoolIt() {
    loader.load("northgate.csv", commaFile(), "SFTP OVERNIGHT");
    LoadResult again = loader.load("northgate-COPY.csv", commaFile(), "UI UPLOAD");

    assertThat(again.wasAlreadySeen()).isTrue();
    assertThat(howManyFilesLoaded()).isEqualTo(1);
  }

  @Test
  @DisplayName("a file with one changed number is a different file, and loads")
  void aChangedFileLoads() {
    loader.load("northgate.csv", commaFile(), "SFTP OVERNIGHT");

    String corrected = commaFile().replace("500", "600");
    LoadResult result = loader.load("northgate.csv", corrected, "SFTP OVERNIGHT");

    assertThat(result.wasAlreadySeen()).isFalse();
    assertThat(howManyWeHold("000000001")).isEqualTo(600.0);
    assertThat(howManyFilesLoaded()).isEqualTo(2);
  }

  @Test
  @DisplayName("bad rows are rejected one by one and the good rows still load")
  void badRowsDoNotSinkTheWholeFile() {
    String file =
        """
        account,identifier,quantity,cost
        ACCOUNT-1,000000001,500,12000
        ACCOUNT-1,000000002,NOT-A-NUMBER,900
        ACCOUNT-1,SHORT,100,900
        ,000000001,100,900
        ACCOUNT-1,000000002,250,4000
        ACCOUNT-1,000000002,250
        """;

    LoadResult result = loader.load("messy.csv", file, "UI UPLOAD");

    assertThat(result.rowsInFile()).isEqualTo(6);
    assertThat(result.rowsLoaded()).isEqualTo(2);
    assertThat(result.rowsRejected()).isEqualTo(4);

    assertThat(howManyWeHold("000000001")).isEqualTo(500.0);
    assertThat(howManyWeHold("000000002")).isEqualTo(250.0);
  }

  @Test
  @DisplayName("every rejected row says which line it was and what was wrong")
  void rejectedRowsExplainThemselves() {
    String file =
        """
        account,identifier,quantity,cost
        ACCOUNT-1,000000002,NOT-A-NUMBER,900
        ACCOUNT-1,SHORT,100,900
        ,000000001,100,900
        """;

    LoadResult result = loader.load("messy.csv", file, "UI UPLOAD");
    List<String> problems = loader.problemsFrom(result.fileLoadId());

    assertThat(problems).hasSize(3);
    assertThat(problems.get(0)).contains("line 2").contains("quantity is not a number");
    assertThat(problems.get(1)).contains("line 3").contains("9 characters");
    assertThat(problems.get(2)).contains("line 4").contains("account name is empty");
  }

  @Test
  @DisplayName("a row for a security we do not know is rejected, not silently dropped")
  void unknownSecurityIsRejected() {
    String file =
        """
        account,identifier,quantity,cost
        ACCOUNT-1,999999999,500,12000
        """;

    LoadResult result = loader.load("unknown.csv", file, "SFTP OVERNIGHT");

    assertThat(result.rowsLoaded()).isZero();
    assertThat(result.rowsRejected()).isEqualTo(1);
    assertThat(loader.problemsFrom(result.fileLoadId()).get(0))
        .contains("no such account or security");
  }

  @Test
  @DisplayName("a heading no custodian uses is refused before anything is loaded")
  void unknownFormatIsRefused() {
    assertThatThrownBy(() -> loader.load("odd.csv", "who,knows,what\n1,2,3\n", "UI UPLOAD"))
        .isInstanceOf(BadLine.class)
        .hasMessageContaining("no custodian format matches");

    assertThat(howManyFilesLoaded()).isZero();
  }

  @Test
  @DisplayName("blank lines in the middle of a file are skipped, not counted as bad")
  void blankLinesAreSkipped() {
    String file =
        """
        account,identifier,quantity,cost
        ACCOUNT-1,000000001,500,12000

        ACCOUNT-1,000000002,250,4000
        """;

    LoadResult result = loader.load("gappy.csv", file, "SFTP OVERNIGHT");

    assertThat(result.rowsLoaded()).isEqualTo(2);
    assertThat(result.rowsRejected()).isZero();
  }

  @Test
  @DisplayName("how the file arrived is recorded against the load")
  void howItArrivedIsRecorded() {
    loader.load("northgate.csv", commaFile(), "UI UPLOAD");

    String arrivedHow = database.queryForObject("SELECT arrived_how FROM file_load", String.class);

    assertThat(arrivedHow).isEqualTo("UI UPLOAD");
  }

  private String commaFile() {
    return """
        account,identifier,quantity,cost
        ACCOUNT-1,000000001,500,12000
        ACCOUNT-1,000000002,250,4000
        """;
  }

  private String pipeFile() {
    return """
        SECURITY|PORTFOLIO|BOOK_COST|UNITS
        000000001|ACCOUNT-1|45000|900
        000000002|ACCOUNT-1|2000|100
        """;
  }

  private double howManyWeHold(String identifier) {
    Double held =
        database.queryForObject(
            "SELECT p.how_many FROM position p"
                + " JOIN product r ON r.product_id = p.product_id"
                + " WHERE r.identifier = ?",
            Double.class,
            identifier);
    return held == null ? 0 : held;
  }

  private int howManyFilesLoaded() {
    Integer counted = database.queryForObject("SELECT count(*) FROM file_load", Integer.class);
    return counted == null ? 0 : counted;
  }

  private static String readFile(String path) {
    try (InputStream stream = FileLoaderTest.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) {
        throw new IllegalStateException("not found on the classpath: " + path);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException problem) {
      throw new IllegalStateException("could not read " + path, problem);
    }
  }
}
