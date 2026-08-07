package vyshaliprabananthlal.ingest;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {

  private static final String DEFAULT_ADDRESS = "jdbc:postgresql://localhost:5432/rtat";
  private static final String DEFAULT_USER = "rtat";
  private static final String DEFAULT_PASSWORD = "rtat_dev_only";

  private Database() {}

  public static String address() {
    return System.getenv().getOrDefault("RTAT_DB_URL", DEFAULT_ADDRESS);
  }

  public static Connection connect() throws SQLException {
    String user = System.getenv().getOrDefault("RTAT_DB_USER", DEFAULT_USER);
    String password = System.getenv().getOrDefault("RTAT_DB_PASSWORD", DEFAULT_PASSWORD);

    Connection connection = DriverManager.getConnection(address(), user, password);
    makeTheDatabaseAgreeOnWhatDayItIs(connection);
    return connection;
  }

  private static void makeTheDatabaseAgreeOnWhatDayItIs(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("SET TIME ZONE 'UTC'");
    }
  }
}
