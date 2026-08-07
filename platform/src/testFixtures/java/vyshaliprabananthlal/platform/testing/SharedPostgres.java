package vyshaliprabananthlal.platform.testing;

import org.testcontainers.postgresql.PostgreSQLContainer;

public final class SharedPostgres {

  private static final PostgreSQLContainer THE_ONLY_ONE =
      new PostgreSQLContainer("postgres:17.10").withDatabaseName("rtat").withReuse(true);

  static {
    THE_ONLY_ONE.start();
  }

  private SharedPostgres() {}

  public static PostgreSQLContainer get() {
    return THE_ONLY_ONE;
  }

  public static String jdbcUrl() {
    return THE_ONLY_ONE.getJdbcUrl();
  }

  public static String user() {
    return THE_ONLY_ONE.getUsername();
  }

  public static String password() {
    return THE_ONLY_ONE.getPassword();
  }
}
