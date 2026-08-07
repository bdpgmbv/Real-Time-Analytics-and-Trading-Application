package vyshaliprabananthlal.stream.plumbing;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class Rows {

  private final JdbcTemplate database;

  public Rows(JdbcTemplate database) {
    this.database = database;
  }

  public <T> List<T> loadOrEmpty(String question, RowMapper<T> reader) {
    return database.query(question, reader);
  }

  public <T> List<T> loadOrComplain(String question, RowMapper<T> reader, String complaint) {
    List<T> loaded = database.query(question, reader);

    if (loaded.isEmpty()) {
      throw new IllegalStateException(complaint);
    }
    return loaded;
  }
}
