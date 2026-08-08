package vyshaliprabananthlal.api.tenant;

import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs a piece of work as one client, so that Postgres row level security applies to it.
 *
 * <p>Every table a client owns carries a policy comparing its {@code client_id} to a setting on
 * the connection. Work run through here has that setting; work run outside it sees nothing,
 * which is the safe way round.
 */
@Component
public class ClientScope {

    /**
     * The third argument is the important one. It makes the setting local to the transaction, so
     * it disappears when the transaction ends and the pooled connection goes back clean.
     *
     * <p>Without it, the next request to borrow that connection would inherit the previous
     * client's identity, which is the failure a connection pool invites.
     */
    private static final String SET_CLIENT_FOR_THIS_TRANSACTION = "SELECT set_config('rtat.client_id', ?, true)";

    private final JdbcTemplate database;

    public ClientScope(JdbcTemplate database) {
        this.database = database;
    }

    /** Reads only. The transaction exists to scope the setting, not to hold locks. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public <T> T reading(int clientId, Supplier<T> work) {
        announceClient(clientId);

        return work.get();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> T writing(int clientId, Supplier<T> work) {
        announceClient(clientId);

        return work.get();
    }

    private void announceClient(int clientId) {
        database.queryForObject(SET_CLIENT_FOR_THIS_TRANSACTION, String.class, String.valueOf(clientId));
    }
}
