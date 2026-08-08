package vyshaliprabananthlal.platform.testing;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

public final class SharedPostgres {

  private static final PostgreSQLContainer INSTANCE =
      new PostgreSQLContainer("postgres:17.10").withDatabaseName("rtat").withReuse(true);

  private static final Set<String> ALREADY_APPLIED = ConcurrentHashMap.newKeySet();

  static {
    INSTANCE.start();
    makeADatabaseForThisModule();
  }

  private SharedPostgres() {}

  public static JdbcTemplate database() {
    DriverManagerDataSource source = new DriverManagerDataSource();
    source.setUrl(jdbcUrl());
    source.setUsername(user());
    source.setPassword(password());

    return new JdbcTemplate(source);
  }

  public static void freshSchema(String schemaSql) {
    if (!ALREADY_APPLIED.add("the schema")) {
      return;
    }

    JdbcTemplate database = database();
    database.execute("DROP SCHEMA public CASCADE");
    database.execute("CREATE SCHEMA public");
    database.execute(schemaSql);
  }

  public static void applyOnce(String name, String sql) {
    if (!ALREADY_APPLIED.add(name)) {
      return;
    }
    database().execute(sql);
  }

  public static String jdbcUrl() {
    String withoutTheDatabase =
        INSTANCE.getJdbcUrl().replaceAll("/[^/?]+(\\?.*)?$", "/" + ourDatabase());

    return withoutTheDatabase;
  }

  public static String user() {
    return INSTANCE.getUsername();
  }

  public static String password() {
    return INSTANCE.getPassword();
  }

  static String ourDatabase() {
    return "rtat_" + System.getProperty("rtat.module", "shared").replace('-', '_');
  }

  private static void makeADatabaseForThisModule() {
    DriverManagerDataSource theStartingPoint = new DriverManagerDataSource();
    theStartingPoint.setUrl(INSTANCE.getJdbcUrl());
    theStartingPoint.setUsername(INSTANCE.getUsername());
    theStartingPoint.setPassword(INSTANCE.getPassword());

    JdbcTemplate first = new JdbcTemplate(theStartingPoint);
    Integer alreadyThere =
        first.queryForObject(
            "SELECT count(*) FROM pg_database WHERE datname = ?", Integer.class, ourDatabase());

    if (alreadyThere == null || alreadyThere == 0) {
      first.execute("CREATE DATABASE " + ourDatabase());
    }
  }
}
