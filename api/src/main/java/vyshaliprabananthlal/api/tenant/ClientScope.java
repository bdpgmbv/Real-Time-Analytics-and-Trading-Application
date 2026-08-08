package vyshaliprabananthlal.api.tenant;

import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ClientScope {

    private static final String TELL_POSTGRES_WHO_IS_ASKING = "SELECT set_config('rtat.client_id', ?, true)";

    private final JdbcTemplate database;

    public ClientScope(JdbcTemplate database) {
        this.database = database;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public <T> T reading(int clientId, Supplier<T> work) {
        setClientOnConnection(clientId);

        return work.get();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> T writing(int clientId, Supplier<T> work) {
        setClientOnConnection(clientId);

        return work.get();
    }

    private void setClientOnConnection(int clientId) {
        database.queryForObject(TELL_POSTGRES_WHO_IS_ASKING, String.class, String.valueOf(clientId));
    }
}
