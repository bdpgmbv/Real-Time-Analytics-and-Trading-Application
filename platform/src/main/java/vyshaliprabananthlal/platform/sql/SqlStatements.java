package vyshaliprabananthlal.platform.sql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Reads a SQL statement out of {@code src/main/resources/sql} by its file name.
 *
 * <p>Statements live in files rather than in Java strings so that they can be read, formatted
 * and explained by a database tool without anyone unpicking string concatenation first.
 *
 * <p>Each one is read once and kept. A missing file fails on first use with the path in the
 * message, rather than producing a confusing SQL error later.
 */
@Component
public class SqlStatements {

    private static final String STATEMENTS_FOLDER = "sql/";

    private final Map<String, String> cached = new ConcurrentHashMap<>();

    public String statement(String name) {
        return cached.computeIfAbsent(name, SqlStatements::readFromClasspath);
    }

    private static String readFromClasspath(String name) {
        String path = STATEMENTS_FOLDER + name + ".sql";

        try (InputStream file = SqlStatements.class.getClassLoader().getResourceAsStream(path)) {
            if (file == null) {
                throw new IllegalStateException("no such statement: " + path);
            }
            return new String(file.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException couldNotRead) {
            throw new IllegalStateException("could not read " + path, couldNotRead);
        }
    }
}
