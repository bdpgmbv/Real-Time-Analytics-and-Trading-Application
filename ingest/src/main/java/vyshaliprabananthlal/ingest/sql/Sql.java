package vyshaliprabananthlal.ingest.sql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class Sql {

  private static final String WHERE_THEY_LIVE = "sql/";

  private final Map<String, String> alreadyRead = new ConcurrentHashMap<>();

  public String statement(String name) {
    return alreadyRead.computeIfAbsent(name, Sql::readResource);
  }

  private static String readResource(String name) {
    String path = WHERE_THEY_LIVE + name + ".sql";

    try (InputStream file = Sql.class.getClassLoader().getResourceAsStream(path)) {
      if (file == null) {
        throw new IllegalStateException("no such statement: " + path);
      }
      return new String(file.readAllBytes(), StandardCharsets.UTF_8).trim();
    } catch (IOException couldNotRead) {
      throw new IllegalStateException("could not read " + path, couldNotRead);
    }
  }
}
