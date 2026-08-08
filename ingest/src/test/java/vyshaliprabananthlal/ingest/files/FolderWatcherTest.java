package vyshaliprabananthlal.ingest.files;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import vyshaliprabananthlal.ingest.format.CommaFormat;
import vyshaliprabananthlal.ingest.format.PipeFormat;
import vyshaliprabananthlal.platform.sql.SqlStatements;
import vyshaliprabananthlal.platform.testing.SharedPostgres;

class FolderWatcherTest {

    private static JdbcTemplate database;

    @TempDir
    Path workingArea;

    private Path incoming;
    private Path finished;
    private FolderWatcher watcher;

    @BeforeAll
    static void buildTheSchema() {
        database = SharedPostgres.database();
        SharedPostgres.freshSchema(readFile("db/1-schema.sql"));
        SharedPostgres.applyOnce(
                "alter-table-position-add-primary-key-acc",
                "ALTER TABLE position ADD PRIMARY KEY (account_id, product_id)");
    }

    @BeforeEach
    void startWithAnEmptyFolder() throws IOException {
        incoming = Files.createDirectories(workingArea.resolve("incoming"));
        finished = workingArea.resolve("finished");

        FileLoader loader = new FileLoader(
                database,
                new FileLoadJournal(database, new SqlStatements()),
                List.of(new CommaFormat(), new PipeFormat()),
                new SqlStatements(),
                new SimpleMeterRegistry());
        watcher = new FolderWatcher(
                new vyshaliprabananthlal.platform.lock.AdvisoryLock(database) {
                    @Override
                    public boolean runExclusively(String name, Runnable work) {
                        work.run();
                        return true;
                    }
                },
                loader,
                incoming.toString(),
                finished.toString());

        database.execute("TRUNCATE file_row_problem, file_load, position, account, fund, client, product,"
                + " currency CASCADE");
        database.execute("INSERT INTO currency VALUES ('USD', 'US Dollar', 2)");
        database.execute("INSERT INTO client (name, size, region) VALUES ('Test', 'SMALL', 'US')");
        database.execute("INSERT INTO fund (client_id, name, reporting_currency)"
                + " SELECT client_id, 'Fund', 'USD' FROM client");
        database.execute("INSERT INTO account (fund_id, name) SELECT fund_id, 'ACCOUNT-1' FROM fund");
        database.execute("INSERT INTO product (kind, name, currency, identifier)"
                + " VALUES ('SHARES', 'Alpha Co', 'USD', '000000001')");
    }

    @Test
    @DisplayName("a file dropped in the folder is loaded and moved aside")
    void aDroppedFileIsLoaded() throws IOException {
        dropAFile("northgate.csv", goodFile());

        watcher.lookForNewFiles();

        assertThat(howManyWeHold()).isEqualTo(500.0);
        assertThat(incoming.resolve("northgate.csv")).doesNotExist();
        assertThat(finished.resolve("northgate.csv")).exists();
    }

    @Test
    @DisplayName("several files in one sweep all load")
    void severalFilesAllLoad() throws IOException {
        dropAFile("one.csv", goodFile());
        dropAFile("two.csv", goodFile().replace("500", "700").replace("ACCOUNT-1", "ACCOUNT-1"));

        watcher.lookForNewFiles();

        assertThat(howManyFilesLoaded()).isEqualTo(2);
    }

    @Test
    @DisplayName("the same file dropped again is recognised and not loaded twice")
    void theSameFileTwiceLoadsOnce() throws IOException {
        dropAFile("northgate.csv", goodFile());
        watcher.lookForNewFiles();

        dropAFile("northgate.csv", goodFile());
        watcher.lookForNewFiles();

        assertThat(howManyFilesLoaded()).isEqualTo(1);
        assertThat(howManyWeHold()).isEqualTo(500.0);
    }

    @Test
    @DisplayName("a file no custodian format matches is moved aside, not left to jam the folder")
    void anUnreadableFileIsMovedAside() throws IOException {
        dropAFile("nonsense.csv", "who,knows,what\n1,2,3\n");

        watcher.lookForNewFiles();

        assertThat(incoming.resolve("nonsense.csv")).doesNotExist();
        assertThat(finished.resolve("nonsense.csv")).exists();
        assertThat(howManyFilesLoaded()).isZero();
    }

    @Test
    @DisplayName("a sweep of an empty folder does nothing and does not fail")
    void anEmptyFolderIsFine() {
        watcher.lookForNewFiles();

        assertThat(howManyFilesLoaded()).isZero();
    }

    @Test
    @DisplayName("a folder that does not exist yet is ignored rather than crashing")
    void aMissingFolderIsIgnored() {
        FileLoader loader = new FileLoader(
                database,
                new FileLoadJournal(database, new SqlStatements()),
                List.of(new CommaFormat()),
                new SqlStatements(),
                new SimpleMeterRegistry());
        FolderWatcher lookingAtNothing = new FolderWatcher(
                new vyshaliprabananthlal.platform.lock.AdvisoryLock(database) {
                    @Override
                    public boolean runExclusively(String name, Runnable work) {
                        work.run();
                        return true;
                    }
                },
                loader,
                workingArea.resolve("not-there").toString(),
                finished.toString());

        lookingAtNothing.lookForNewFiles();

        assertThat(howManyFilesLoaded()).isZero();
    }

    @Test
    @DisplayName("a file with bad rows still loads its good rows and is moved aside")
    void aMessyFileStillLoadsWhatItCan() throws IOException {
        dropAFile("messy.csv", """
        account,identifier,quantity,cost
        ACCOUNT-1,000000001,500,12000
        ACCOUNT-1,000000001,NOT-A-NUMBER,900
        """);

        watcher.lookForNewFiles();

        assertThat(howManyWeHold()).isEqualTo(500.0);
        assertThat(finished.resolve("messy.csv")).exists();

        Integer rejected = database.queryForObject("SELECT rows_rejected FROM file_load", Integer.class);
        assertThat(rejected).isEqualTo(1);
    }

    private void dropAFile(String name, String contents) throws IOException {
        Files.writeString(incoming.resolve(name), contents, StandardCharsets.UTF_8);
    }

    private String goodFile() {
        return """
        account,identifier,quantity,cost
        ACCOUNT-1,000000001,500,12000
        """;
    }

    private double howManyWeHold() {
        Double held = database.queryForObject("SELECT quantity FROM position", Double.class);
        return held == null ? 0 : held;
    }

    private int howManyFilesLoaded() {
        Integer counted = database.queryForObject("SELECT count(*) FROM file_load", Integer.class);
        return counted == null ? 0 : counted;
    }

    private static String readFile(String path) {
        try (InputStream stream = FolderWatcherTest.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("not found on the classpath: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException problem) {
            throw new IllegalStateException("could not read " + path, problem);
        }
    }
}
