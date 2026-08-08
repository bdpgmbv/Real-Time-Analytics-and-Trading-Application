package vyshaliprabananthlal.platform.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import vyshaliprabananthlal.platform.testing.SharedPostgres;

class AdvisoryLockTest {

    private static DriverManagerDataSource source;

    @BeforeAll
    static void connect() {
        source = new DriverManagerDataSource();
        source.setUrl(SharedPostgres.jdbcUrl());
        source.setUsername(SharedPostgres.user());
        source.setPassword(SharedPostgres.password());
    }

    @Test
    @DisplayName("one instance on its own gets the lock and does the work")
    void aloneItRuns() {
        AtomicInteger ran = new AtomicInteger();

        assertThat(anInstance().runExclusively("sweep-the-folder", ran::incrementAndGet))
                .isTrue();
        assertThat(ran.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("the lock is released when the work finishes, so the next turn runs too")
    void theNextTurnRunsAgain() {
        AdvisoryLock lock = anInstance();
        AtomicInteger ran = new AtomicInteger();

        lock.runExclusively("sweep-the-folder", ran::incrementAndGet);
        lock.runExclusively("sweep-the-folder", ran::incrementAndGet);

        assertThat(ran.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("two instances sweeping at once: exactly one does the work")
    void twoInstancesAndOnlyOneRuns() throws Exception {
        AtomicInteger timesTheWorkRan = new AtomicInteger();
        AtomicInteger timesItWasSkipped = new AtomicInteger();

        CountDownLatch bothInside = new CountDownLatch(1);
        CountDownLatch bothDone = new CountDownLatch(2);

        for (int instance = 0; instance < 2; instance++) {
            new Thread(() -> {
                        try {
                            boolean itRanHere = anInstance().runExclusively("sweep-the-folder", () -> {
                                timesTheWorkRan.incrementAndGet();
                                await(bothInside);
                            });
                            if (!itRanHere) {
                                timesItWasSkipped.incrementAndGet();
                                bothInside.countDown();
                            }
                        } finally {
                            bothDone.countDown();
                        }
                    })
                    .start();
        }

        bothInside.countDown();
        assertThat(bothDone.await(20, TimeUnit.SECONDS)).isTrue();

        assertThat(timesTheWorkRan.get()).isEqualTo(1);
        assertThat(timesItWasSkipped.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("different jobs do not block each other")
    void differentJobsAreIndependent() {
        AdvisoryLock lock = anInstance();
        AtomicInteger ran = new AtomicInteger();

        assertThat(lock.runExclusively("sweep-the-folder", ran::incrementAndGet))
                .isTrue();
        assertThat(lock.runExclusively("push-the-screens", ran::incrementAndGet))
                .isTrue();
        assertThat(ran.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("work that throws releases the lock rather than holding it forever")
    void aFailureStillReleasesTheLock() {
        AdvisoryLock lock = anInstance();

        assertThatThrownBy(() -> lock.runExclusively("sweep-the-folder", () -> {
                    throw new IllegalStateException("the folder was not there");
                }))
                .isInstanceOf(IllegalStateException.class);

        AtomicInteger ran = new AtomicInteger();
        assertThat(lock.runExclusively("sweep-the-folder", ran::incrementAndGet))
                .isTrue();
    }

    @Test
    @DisplayName("the same job name always gives the same lock, on every instance")
    void theSameNameGivesTheSameLock() {
        assertThat(AdvisoryLock.keyFor("sweep-the-folder")).isEqualTo(AdvisoryLock.keyFor("sweep-the-folder"));
        assertThat(AdvisoryLock.keyFor("sweep-the-folder")).isNotEqualTo(AdvisoryLock.keyFor("push-the-screens"));
    }

    /** A separate AdvisoryLock on its own transaction manager is what a second instance looks like. */
    private AdvisoryLock anInstance() {
        return new AdvisoryLock(new JdbcTemplate(source)) {
            @Override
            public boolean runExclusively(String lockName, Runnable work) {
                return Boolean.TRUE.equals(new TransactionTemplate(new DataSourceTransactionManager(source))
                        .execute(status -> super.runExclusively(lockName, work)));
            }
        };
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
