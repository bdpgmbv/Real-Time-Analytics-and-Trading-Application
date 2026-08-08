package vyshaliprabananthlal.calculate.hedge;

import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vyshaliprabananthlal.calculate.hedge.HedgeAdviser.Recommendation;
import vyshaliprabananthlal.platform.sql.SqlStatements;

/**
 * Records what was actually sent to the market.
 *
 * <p>Both numbers are kept: what we suggested and what the client chose. They are often
 * different, and the difference is the thing a person has to answer for later.
 *
 * <p>All of one batch is written in one transaction. Half a set of hedges reaching the market is
 * worse than none of them.
 */
@Service
public class HedgeBook {

    private final JdbcTemplate database;
    private final SqlStatements statements;

    public HedgeBook(JdbcTemplate database, SqlStatements statements) {
        this.database = database;
        this.statements = statements;
    }

    /**
     * Writes the hedges the client accepted and returns their ids.
     *
     * @param advice what we suggested, in order
     * @param chosenAmounts what the client chose, one per recommendation. Zero means declined.
     * @param sentBy the person who pressed the button
     * @return the id of every hedge actually sent
     */
    @Transactional
    public List<Long> submit(int fundId, List<Recommendation> advice, List<Double> chosenAmounts, String sentBy) {

        if (advice.size() != chosenAmounts.size()) {
            throw new IllegalArgumentException(
                    "got " + advice.size() + " recommendations but " + chosenAmounts.size() + " answers");
        }

        long firstHedgeId = nextHedgeId();
        List<Long> sent = new ArrayList<>();

        for (int index = 0; index < advice.size(); index++) {
            Recommendation recommendation = advice.get(index);
            double chosen = chosenAmounts.get(index);

            if (chosen == 0) {
                continue;
            }

            long hedgeId = firstHedgeId + sent.size();

            database.update(
                    statements.statement("insert-hedge"),
                    hedgeId,
                    fundId,
                    recommendation.currency(),
                    recommendation.exposure(),
                    recommendation.suggestedAmount(),
                    chosen,
                    recommendation.instrument(),
                    sentBy,
                    "FXM-" + hedgeId);

            sent.add(hedgeId);
        }
        return sent;
    }

    private long nextHedgeId() {
        Long next = database.queryForObject(statements.statement("select-next-hedge-id"), Long.class);

        return next == null ? 1 : next;
    }
}
