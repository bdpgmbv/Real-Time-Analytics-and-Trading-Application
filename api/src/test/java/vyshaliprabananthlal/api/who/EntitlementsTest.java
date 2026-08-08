package vyshaliprabananthlal.api.who;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import vyshaliprabananthlal.platform.sql.SqlStatements;
import vyshaliprabananthlal.platform.testing.SharedPostgres;

class EntitlementsTest {

    private static JdbcTemplate database;

    private Entitlements entitlements;

    @BeforeAll
    static void buildTheSchema() {
        database = SharedPostgres.database();
        SharedPostgres.freshSchema(readFile("db/1-schema.sql"));
    }

    @BeforeEach
    void twoCompaniesWithTheirOwnFunds() {
        entitlements = new Entitlements(database, new SqlStatements());

        database.execute("TRUNCATE entitlement, app_user, account, fund, client, currency CASCADE");
        database.execute("INSERT INTO currency VALUES ('USD','US Dollar',2),('EUR','Euro',2)");
        database.execute("INSERT INTO client (client_id,name,size,region) OVERRIDING SYSTEM VALUE"
                + " VALUES (1,'Adriatic Capital','LARGE','EUROPE'),(2,'Nordwind Partners','SMALL','US')");
        database.execute("INSERT INTO fund (fund_id,client_id,name,reporting_currency) OVERRIDING SYSTEM VALUE"
                + " VALUES (10,1,'Adriatic Growth','USD'),(11,1,'Adriatic Income','EUR'),"
                + "        (20,2,'Nordwind Alpha','USD')");
        database.execute("INSERT INTO account (account_id,fund_id,name) OVERRIDING SYSTEM VALUE"
                + " VALUES (100,10,'A1'),(101,10,'A2'),(200,20,'N1')");
        database.execute("INSERT INTO app_user VALUES ('adriatic-reader',1,'A. Fischer','af@example.com'),"
                + " ('adriatic-trader',1,'R. Baumann','rb@example.com'),"
                + " ('nordwind-reader',2,'M. Delgado','md@example.com')");
        database.execute("INSERT INTO entitlement VALUES ('adriatic-reader',10,false),"
                + " ('adriatic-reader',11,false),"
                + " ('adriatic-trader',10,true),"
                + " ('nordwind-reader',20,false)");
    }

    @Test
    @DisplayName("a user sees their own company's funds and no others")
    void aUserSeesOnlyTheirOwnFunds() {
        List<Entitlements.VisibleFund> visible = entitlements.fundsVisibleTo("adriatic-reader");

        assertThat(visible).extracting(Entitlements.VisibleFund::fundId).containsExactly(10, 11);
    }

    @Test
    @DisplayName("another company's fund is not in the list at all")
    void anotherCompanysFundIsNotListed() {
        List<Entitlements.VisibleFund> visible = entitlements.fundsVisibleTo("adriatic-reader");

        assertThat(visible).extracting(Entitlements.VisibleFund::fundId).doesNotContain(20);
    }

    @Test
    @DisplayName("asking directly for another company's fund is refused, not quietly emptied")
    void askingForAnotherCompanysFundIsRefused() {
        assertThatThrownBy(() -> entitlements.requireVisible("adriatic-reader", 20))
                .isInstanceOf(Entitlements.NotAllowed.class)
                .hasMessageContaining("cannot see fund 20");
    }

    @Test
    @DisplayName("a fund that does not exist is refused the same way, leaking nothing")
    void aFundThatDoesNotExistLeaksNothing() {
        assertThatThrownBy(() -> entitlements.requireVisible("adriatic-reader", 999))
                .isInstanceOf(Entitlements.NotAllowed.class)
                .hasMessageContaining("cannot see fund 999");
    }

    @Test
    @DisplayName("a user with no entitlements at all sees nothing")
    void aUserWithNoEntitlementsSeesNothing() {
        assertThat(entitlements.fundsVisibleTo("nobody-at-all")).isEmpty();
    }

    @Test
    @DisplayName("looking is allowed but sending trades is a separate permission")
    void lookingAndTradingAreSeparate() {
        assertThatCode(() -> entitlements.requireVisible("adriatic-reader", 10)).doesNotThrowAnyException();

        assertThatThrownBy(() -> entitlements.requireTradePermission("adriatic-reader", 10))
                .isInstanceOf(Entitlements.NotAllowed.class)
                .hasMessageContaining("not send trades");
    }

    @Test
    @DisplayName("a user who may trade may also look")
    void aTraderMayAlsoLook() {
        assertThatCode(() -> entitlements.requireTradePermission("adriatic-trader", 10))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("trying to trade on a fund you cannot even see says you cannot see it")
    void tradingOnAnInvisibleFund() {
        assertThatThrownBy(() -> entitlements.requireTradePermission("nordwind-reader", 10))
                .isInstanceOf(Entitlements.NotAllowed.class)
                .hasMessageContaining("cannot see fund 10");
    }

    @Test
    @DisplayName("asking for accounts you may see gives them back")
    void accountsYouMaySeeComeBack() {
        List<Integer> allowed = entitlements.filterVisible("adriatic-reader", 10, List.of(100, 101));

        assertThat(allowed).containsExactly(100, 101);
    }

    @Test
    @DisplayName("slipping another company's account into the list drops it silently")
    void anotherCompanysAccountIsDropped() {
        List<Integer> allowed = entitlements.filterVisible("adriatic-reader", 10, List.of(100, 200));

        assertThat(allowed).containsExactly(100);
    }

    @Test
    @DisplayName("an account belonging to a different fund of your own company is dropped too")
    void anAccountFromAnotherFundIsDropped() {
        database.execute("INSERT INTO account (account_id,fund_id,name) OVERRIDING SYSTEM VALUE VALUES (110,11,'B1')");

        List<Integer> allowed = entitlements.filterVisible("adriatic-reader", 10, List.of(100, 110));

        assertThat(allowed).containsExactly(100);
    }

    @Test
    @DisplayName("asking for no accounts asks the database nothing")
    void askingForNothingReturnsNothing() {
        assertThat(entitlements.filterVisible("adriatic-reader", 10, List.of())).isEmpty();
    }

    private static String readFile(String path) {
        try (InputStream stream = EntitlementsTest.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("not found on the classpath: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException problem) {
            throw new IllegalStateException("could not read " + path, problem);
        }
    }
}
