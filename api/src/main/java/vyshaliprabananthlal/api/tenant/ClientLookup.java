package vyshaliprabananthlal.api.tenant;

import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vyshaliprabananthlal.api.who.Entitlements;
import vyshaliprabananthlal.calculate.exposure.CacheConfig;
import vyshaliprabananthlal.platform.sql.SqlStatements;

/**
 * Which client a signed-in user belongs to.
 *
 * <p>This is the first thing every request needs, because it decides what row level security
 * will let the rest of the request see. A user we do not recognise is refused rather than given
 * an empty answer.
 */
@Service
public class ClientLookup {

    private final JdbcTemplate database;
    private final SqlStatements statements;

    public ClientLookup(JdbcTemplate database, SqlStatements statements) {
        this.database = database;
        this.statements = statements;
    }

    @Cacheable(CacheConfig.USER_CLIENT)
    public int forUser(String userId) {
        List<Integer> found = database.queryForList(statements.statement("select-user-client"), Integer.class, userId);

        if (found.isEmpty()) {
            throw new Entitlements.NotAllowed("we do not know who you are");
        }
        return found.get(0);
    }
}
