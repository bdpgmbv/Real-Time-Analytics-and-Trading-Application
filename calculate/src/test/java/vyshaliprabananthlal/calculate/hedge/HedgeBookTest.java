package vyshaliprabananthlal.calculate.hedge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import vyshaliprabananthlal.calculate.exposure.RealDatabase;
import vyshaliprabananthlal.calculate.sql.Sql;

class HedgeBookTest {

    private static JdbcTemplate database;

    private HedgeBook book;

    @BeforeAll
    static void buildTheSchema() {
        database = RealDatabase.readyToUse();
    }

    @BeforeEach
    void oneFundReportingInUsd() {
        book = new HedgeBook(database, new Sql());

        database.execute("TRUNCATE hedge_fill, hedge, fund, client, currency CASCADE");
        database.execute("INSERT INTO currency VALUES ('USD','US Dollar',2),('EUR','Euro',2),('GBP','Pound',2)");
        database.execute("INSERT INTO client (name,size,region) VALUES ('Test','LARGE','EUROPE')");
        database.execute("INSERT INTO fund (fund_id,client_id,name,reporting_currency) OVERRIDING SYSTEM VALUE"
                + " SELECT 1, client_id, 'Fund', 'USD' FROM client");
    }

    @Test
    @DisplayName("a hedge the client accepted is written out as SENT")
    void anAcceptedHedgeIsSent() {
        List<Long> sent = book.submit(1, List.of(euroAdvice()), List.of(-5000000.0), "a.person");

        assertThat(sent).hasSize(1);
        assertThat(statusOf(sent.get(0))).isEqualTo("SENT");
        assertThat(columnOf(sent.get(0), "chosen_amount")).isEqualTo(-5000000.0);
        assertThat(columnOf(sent.get(0), "suggested_amount")).isEqualTo(-5000000.0);
    }

    @Test
    @DisplayName("what the client chose is kept even when it differs from what we suggested")
    void whatTheClientChoseIsKept() {
        List<Long> sent = book.submit(1, List.of(euroAdvice()), List.of(-3000000.0), "a.person");

        assertThat(columnOf(sent.get(0), "suggested_amount")).isEqualTo(-5000000.0);
        assertThat(columnOf(sent.get(0), "chosen_amount")).isEqualTo(-3000000.0);
    }

    @Test
    @DisplayName("a recommendation the client declined is not sent at all")
    void aDeclinedRecommendationIsNotSent() {
        List<Long> sent = book.submit(1, List.of(euroAdvice()), List.of(0.0), "a.person");

        assertThat(sent).isEmpty();
        assertThat(howManyHedges()).isZero();
    }

    @Test
    @DisplayName("several hedges sent together each get their own number and reference")
    void severalHedgesGetTheirOwnNumbers() {
        List<Long> sent =
                book.submit(1, List.of(euroAdvice(), poundAdvice()), List.of(-5000000.0, -2000000.0), "a.person");

        assertThat(sent).hasSize(2);
        assertThat(sent.get(1)).isEqualTo(sent.get(0) + 1);
        assertThat(referenceOf(sent.get(0))).isEqualTo("FXM-" + sent.get(0));
        assertThat(referenceOf(sent.get(1))).isEqualTo("FXM-" + sent.get(1));
    }

    @Test
    @DisplayName("a second batch carries on numbering, it does not reuse a number")
    void numberingCarriesOn() {
        List<Long> first = book.submit(1, List.of(euroAdvice()), List.of(-1.0), "a.person");
        List<Long> second = book.submit(1, List.of(poundAdvice()), List.of(-1.0), "a.person");

        assertThat(second.get(0)).isGreaterThan(first.get(0));
        assertThat(howManyHedges()).isEqualTo(2);
    }

    @Test
    @DisplayName("a mismatch between advice and answers is refused before anything is written")
    void aMismatchIsRefused() {
        assertThatThrownBy(() -> book.submit(1, List.of(euroAdvice(), poundAdvice()), List.of(-1.0), "a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 recommendations but 1 answers");

        assertThat(howManyHedges()).isZero();
    }

    @Test
    @DisplayName("who sent it is recorded, because a person has to answer for it later")
    void whoSentItIsRecorded() {
        List<Long> sent = book.submit(1, List.of(euroAdvice()), List.of(-1.0), "r.baumann");

        assertThat(oneText("SELECT sent_by FROM hedge WHERE hedge_id = ?", sent.get(0)))
                .isEqualTo("r.baumann");
    }

    private HedgeAdviser.Recommendation euroAdvice() {
        return new HedgeAdviser.Recommendation("EUR", 5000000, -5000000, "FORWARD", "because");
    }

    private HedgeAdviser.Recommendation poundAdvice() {
        return new HedgeAdviser.Recommendation("GBP", 2000000, -2000000, "FORWARD", "because");
    }

    private String statusOf(long hedgeId) {
        return oneText("SELECT status FROM hedge WHERE hedge_id = ?", hedgeId);
    }

    private String referenceOf(long hedgeId) {
        return oneText("SELECT external_reference FROM hedge WHERE hedge_id = ?", hedgeId);
    }

    private String oneText(String question, long hedgeId) {
        String found = database.queryForObject(question, String.class, hedgeId);
        return found == null ? "" : found;
    }

    private double columnOf(long hedgeId, String column) {
        Double value =
                database.queryForObject("SELECT " + column + " FROM hedge WHERE hedge_id = ?", Double.class, hedgeId);
        return value == null ? 0 : value;
    }

    private int howManyHedges() {
        Integer counted = database.queryForObject("SELECT count(*) FROM hedge", Integer.class);
        return counted == null ? 0 : counted;
    }
}
