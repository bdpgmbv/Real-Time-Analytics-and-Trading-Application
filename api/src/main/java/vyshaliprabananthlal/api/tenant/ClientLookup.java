package vyshaliprabananthlal.api.tenant;

import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vyshaliprabananthlal.api.who.Entitlements;
import vyshaliprabananthlal.platform.sql.SqlStatements;

@Service
public class ClientLookup {

    public static final String THE_CACHE = "which client a user belongs to";

    private final JdbcTemplate database;
    private final SqlStatements statements;

    public ClientLookup(JdbcTemplate database, SqlStatements statements) {
        this.database = database;
        this.statements = statements;
    }

    @Cacheable(THE_CACHE)
    public int forUser(String userId) {
        List<Integer> found = database.queryForList(statements.statement("select-user-client"), Integer.class, userId);

        if (found.isEmpty()) {
            throw new Entitlements.NotAllowed("we do not know who you are");
        }
        return found.get(0);
    }
}
