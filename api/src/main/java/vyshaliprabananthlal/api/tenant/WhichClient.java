package vyshaliprabananthlal.api.tenant;

import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vyshaliprabananthlal.api.who.NotAllowedException;
import vyshaliprabananthlal.calculate.sql.Sql;

@Service
public class WhichClient {

  public static final String THE_CACHE = "which client a user belongs to";

  private final JdbcTemplate database;
  private final Sql sql;

  public WhichClient(JdbcTemplate database, Sql sql) {
    this.database = database;
    this.sql = sql;
  }

  @Cacheable(THE_CACHE)
  public int forUser(String userId) {
    List<Integer> found =
        database.queryForList(sql.statement("which-client-is-this-user"), Integer.class, userId);

    if (found.isEmpty()) {
      throw new NotAllowedException("we do not know who you are");
    }
    return found.get(0);
  }
}
