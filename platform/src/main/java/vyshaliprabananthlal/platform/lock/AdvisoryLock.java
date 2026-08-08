package vyshaliprabananthlal.platform.lock;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lets scheduled work run on exactly one instance at a time.
 *
 * <p>Two copies of a service both have their own scheduler. Without this, two instances sweep the
 * same SFTP folder and two instances push the same screen update. The file fingerprint happens to
 * make the first harmless today, which is luck rather than design.
 *
 * <p>Postgres advisory locks are used rather than a lock table or a library. They need no schema,
 * and the transaction-scoped form releases itself when the transaction ends — including when the
 * instance holding it dies, because the connection dies with it. A lock table would need a lease,
 * a clock, and a decision about what to do with a row whose owner never came back.
 */
@Component
public class AdvisoryLock {

    private static final Logger LOG = LoggerFactory.getLogger(AdvisoryLock.class);

    /** Transaction-scoped: released at commit or rollback, and on a lost connection. */
    private static final String TRY_TO_TAKE_IT = "SELECT pg_try_advisory_xact_lock(?)";

    private final JdbcTemplate database;

    public AdvisoryLock(JdbcTemplate database) {
        this.database = database;
    }

    /**
     * Runs the work if this instance wins the lock, and does nothing if another instance holds it.
     *
     * @return true if the work ran here
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean runExclusively(String lockName, Runnable work) {
        if (!Boolean.TRUE.equals(database.queryForObject(TRY_TO_TAKE_IT, Boolean.class, keyFor(lockName)))) {
            LOG.debug("another instance is doing {}, skipping this turn", lockName);
            return false;
        }

        work.run();
        return true;
    }

    /**
     * Postgres takes a number, not a name. A checksum of the name gives every instance of the
     * service the same number without anyone having to keep a list of them.
     */
    static long keyFor(String lockName) {
        CRC32 checksum = new CRC32();
        checksum.update(lockName.getBytes(StandardCharsets.UTF_8));

        return checksum.getValue();
    }
}
