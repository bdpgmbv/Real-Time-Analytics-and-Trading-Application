package vyshaliprabananthlal.jobs.exposure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vyshaliprabananthlal.platform.testing.SharedPostgres;

class LoadWhoHoldsWhatTest {

  @BeforeAll
  static void twoFundsHoldingTheSameSecurity() throws SQLException {
    SharedPostgres.freshSchema(readFile("db/1-schema.sql"));
    run(
        "INSERT INTO currency VALUES ('USD','US Dollar',2),('EUR','Euro',2);"
            + "INSERT INTO client (client_id,name,size,region) OVERRIDING SYSTEM VALUE"
            + " VALUES (1,'Test','LARGE','EUROPE');"
            + "INSERT INTO fund (fund_id,client_id,name,reporting_currency) OVERRIDING SYSTEM VALUE"
            + " VALUES (10,1,'One','USD'),(11,1,'Two','USD');"
            + "INSERT INTO account (account_id,fund_id,name) OVERRIDING SYSTEM VALUE"
            + " VALUES (100,10,'A'),(101,10,'B'),(200,11,'C');"
            + "INSERT INTO product (product_id,kind,name,currency,identifier)"
            + " OVERRIDING SYSTEM VALUE VALUES (500,'SHARES','Airbus','EUR','000000500');"
            + "INSERT INTO position (account_id,product_id,quantity,cost,is_hedge,"
            + "position_date) VALUES (100,500,6,0,false,CURRENT_DATE),"
            + " (101,500,4,0,false,CURRENT_DATE),(200,500,100,0,false,CURRENT_DATE);");
  }

  @Test
  @DisplayName("two accounts in one fund are rolled into a single holding for that fund")
  void accountsInAFundAreRolledUp() throws SQLException {
    Map<Integer, List<FundHolding>> whoHoldsWhat = load();

    List<FundHolding> holders = whoHoldsWhat.get(500);

    assertThat(holders).hasSize(2);
    assertThat(holders).extracting(FundHolding::howMany).containsExactlyInAnyOrder(10.0, 100.0);
  }

  @Test
  @DisplayName("the currency comes from the security, not from the fund")
  void theCurrencyComesFromTheSecurity() throws SQLException {
    assertThat(load().get(500)).allMatch(one -> one.currency().equals("EUR"));
  }

  @Test
  @DisplayName("each fund holding it appears once")
  void eachFundAppearsOnce() throws SQLException {
    assertThat(load().get(500)).extracting(FundHolding::fundId).containsExactlyInAnyOrder(10, 11);
  }

  @Test
  @DisplayName("the count of holdings is the size of the work a price tick can cause")
  void theCountIsTheWorkAPriceTickCauses() throws SQLException {
    assertThat(HoldingsLoader.holdingCount(load())).isEqualTo(2);
  }

  private Map<Integer, List<FundHolding>> load() throws SQLException {
    return HoldingsLoader.from(
        SharedPostgres.jdbcUrl(), SharedPostgres.user(), SharedPostgres.password());
  }

  private static void run(String sql) throws SQLException {
    try (Connection database =
            DriverManager.getConnection(
                SharedPostgres.jdbcUrl(), SharedPostgres.user(), SharedPostgres.password());
        Statement statement = database.createStatement()) {
      statement.execute(sql);
    }
  }

  private static String readFile(String path) {
    try (InputStream stream =
        LoadWhoHoldsWhatTest.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) {
        throw new IllegalStateException("not found on the classpath: " + path);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException problem) {
      throw new IllegalStateException("could not read " + path, problem);
    }
  }
}
