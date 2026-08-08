package vyshaliprabananthlal.stream.plumbing;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class QueryRunner {

  private final JdbcTemplate database;

  public QueryRunner(JdbcTemplate database) {
    this.database = database;
  }

  public <T> List<T> query(String question, RowMapper<T> reader) {
    return database.query(question, reader);
  }

  public <T> List<T> queryRequired(String question, RowMapper<T> reader, String complaint) {
    List<T> loaded = database.query(question, reader);

    if (loaded.isEmpty()) {
      throw new IllegalStateException(complaint);
    }
    return loaded;
  }
}
