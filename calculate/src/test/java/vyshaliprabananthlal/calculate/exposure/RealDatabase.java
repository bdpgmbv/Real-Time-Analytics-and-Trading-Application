package vyshaliprabananthlal.calculate.exposure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.jdbc.core.JdbcTemplate;
import vyshaliprabananthlal.platform.testing.SharedPostgres;

public final class RealDatabase {

  private RealDatabase() {}

  public static JdbcTemplate readyToUse() {
    SharedPostgres.freshSchema(readFile("db/1-schema.sql"));
    SharedPostgres.applyOnce(
        "position-pk", "ALTER TABLE position ADD PRIMARY KEY (account_id, product_id)");
    SharedPostgres.applyOnce(
        "price-pk", "ALTER TABLE price ADD PRIMARY KEY (product_id, price_date)");

    return SharedPostgres.database();
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
