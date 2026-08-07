package vyshaliprabananthlal.calculate.exposure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

public final class RealDatabase {

  private RealDatabase() {}

  public static JdbcTemplate startedFrom(PostgreSQLContainer container) {
    DriverManagerDataSource source = new DriverManagerDataSource();
    source.setUrl(container.getJdbcUrl());
    source.setUsername(container.getUsername());
    source.setPassword(container.getPassword());

    JdbcTemplate database = new JdbcTemplate(source);
    database.execute(readFile("db/1-schema.sql"));
    database.execute("ALTER TABLE position ADD PRIMARY KEY (account_id, product_id)");
    database.execute("ALTER TABLE price ADD PRIMARY KEY (product_id, price_date)");

    return database;
  }

  public static String readFile(String path) {
    try (InputStream stream = RealDatabase.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) {
        throw new IllegalStateException("not found on the classpath: " + path);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException problem) {
      throw new IllegalStateException("could not read " + path, problem);
    }
  }
}
