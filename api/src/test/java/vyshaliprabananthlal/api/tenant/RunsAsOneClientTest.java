package vyshaliprabananthlal.api.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import vyshaliprabananthlal.platform.testing.SharedPostgres;

class RunsAsOneClientTest {

  private static final String READ_IT = "SELECT current_setting('rtat.client_id', true)";
  private static final String SET_IT = "SELECT set_config('rtat.client_id', ?, true)";

  private static JdbcTemplate database;
  private static TransactionTemplate inATransaction;

  @BeforeAll
  static void connect() {
    DriverManagerDataSource source = new DriverManagerDataSource();
    source.setUrl(SharedPostgres.jdbcUrl());
    source.setUsername(SharedPostgres.user());
    source.setPassword(SharedPostgres.password());

    database = new JdbcTemplate(source);
    inATransaction = new TransactionTemplate(new DataSourceTransactionManager(source));
  }

  @Test
  @DisplayName("inside the transaction, Postgres knows which client is asking")
  void insideTheTransactionTheClientIsSet() {
    String seen =
        inATransaction.execute(
            status -> {
              setTo("7");
              return whatPostgresThinks();
            });

    assertThat(seen).isEqualTo("7");
  }

  @Test
  @DisplayName("when the transaction ends the setting goes with it, so the next request is clean")
  void theSettingDoesNotOutliveTheTransaction() {
    inATransaction.executeWithoutResult(status -> setTo("7"));

    assertThat(whatPostgresThinks()).isEmpty();
  }

  @Test
  @DisplayName("one client's context cannot leak into the next client's request")
  void oneClientDoesNotLeakIntoTheNext() {
    inATransaction.executeWithoutResult(status -> setTo("1"));

    String whatTheNextRequestSees = inATransaction.execute(status -> whatPostgresThinks());

    assertThat(whatTheNextRequestSees).isEmpty();
  }

  @Test
  @DisplayName("a rolled back transaction leaves nothing behind either")
  void aRollbackLeavesNothingBehind() {
    assertThatThrownBy(
            () ->
                inATransaction.execute(
                    status -> {
                      setTo("9");
                      throw new IllegalStateException("something went wrong");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(whatPostgresThinks()).isEmpty();
  }

  private static void setTo(String clientId) {
    database.queryForList(SET_IT, String.class, clientId);
  }

  private static String whatPostgresThinks() {
    String found = database.queryForObject(READ_IT, String.class);

    return found == null ? "" : found;
  }
}
