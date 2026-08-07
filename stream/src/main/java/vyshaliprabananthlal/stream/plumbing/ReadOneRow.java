package vyshaliprabananthlal.stream.plumbing;

import java.sql.ResultSet;
import java.sql.SQLException;

public interface ReadOneRow<T> {

  T from(ResultSet row) throws SQLException;
}
