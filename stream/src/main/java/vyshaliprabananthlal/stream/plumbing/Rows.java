package vyshaliprabananthlal.stream.plumbing;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class Rows {

  private Rows() {}

  public static <T> List<T> loadFrom(String question, ReadOneRow<T> reader) throws SQLException {
    List<T> loaded = new ArrayList<>();

    try (Connection database = Database.connect();
        Statement asking = database.createStatement();
        ResultSet answer = asking.executeQuery(question)) {

      while (answer.next()) {
        loaded.add(reader.from(answer));
      }
    }
    return loaded;
  }

  public static <T> List<T> loadOrComplain(String question, ReadOneRow<T> reader, String complaint)
      throws SQLException {

    List<T> loaded = loadFrom(question, reader);

    if (loaded.isEmpty()) {
      throw new IllegalStateException(complaint);
    }
    return loaded;
  }
}
