package vyshaliprabananthlal.api.resilience;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

/**
 * Wraps a read of the database so that a moment's trouble does not become an error on a screen,
 * and a long outage does not become a queue of waiting requests.
 *
 * <p>Two different problems, two different answers.
 *
 * <p><b>Retry</b> is for the blip: a connection dropped mid-query, a failover that took two
 * seconds. Trying again usually works, and only transient failures are retried — asking twice
 * for a fund that does not exist would just be slower.
 *
 * <p><b>The circuit breaker</b> is for the outage. Once enough calls have failed it stops trying
 * for a while and fails immediately. That sounds worse and is better: without it every request
 * waits for its own timeout, the connection pool fills with calls that cannot succeed, and a
 * database problem becomes an API problem for every client at once.
 */
@Component
public class DatabaseCalls {

    static final String NAME = "database";

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseCalls.class);

    @Retry(name = NAME)
    @CircuitBreaker(name = NAME, fallbackMethod = "theDatabaseIsNotAnswering")
    public <T> T read(Supplier<T> work) {
        return work.get();
    }

    /**
     * Reached when the breaker is open, or when the retries ran out.
     *
     * <p>It rethrows rather than inventing an answer. A screen showing a stale or empty exposure
     * without saying so is worse than a screen saying it could not load: somebody would trade on
     * it.
     */
    @SuppressWarnings("unused")
    private <T> T theDatabaseIsNotAnswering(Supplier<T> work, Throwable why) {
        LOG.error("the database is not answering: {}", why.getMessage());

        throw new DatabaseUnavailable("the database is not answering, please try again shortly", why);
    }

    /** Distinct from a transient failure, so the web layer can answer 503 rather than 500. */
    public static class DatabaseUnavailable extends TransientDataAccessException {

        private static final long serialVersionUID = 1L;

        public DatabaseUnavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
