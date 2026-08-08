package vyshaliprabananthlal.calculate.hedge;

import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vyshaliprabananthlal.platform.sql.SqlStatements;

@Service
public class HedgeBook {

    private final JdbcTemplate database;
    private final SqlStatements statements;

    public HedgeBook(JdbcTemplate database, SqlStatements statements) {
        this.database = database;
        this.statements = statements;
    }

    @Transactional
    public List<Long> submit(
            int fundId, List<HedgeAdviser.Recommendation> advice, List<Double> whatTheClientChose, String whoSentIt) {

        if (advice.size() != whatTheClientChose.size()) {
            throw new IllegalArgumentException(
                    "got " + advice.size() + " recommendations but " + whatTheClientChose.size() + " answers");
        }

        long nextNumber = nextHedgeId();
        List<Long> sent = new ArrayList<>();

        for (int which = 0; which < advice.size(); which++) {
            HedgeAdviser.Recommendation thisOne = advice.get(which);
            double chosen = whatTheClientChose.get(which);

            if (chosen == 0) {
                continue;
            }

            long hedgeNumber = nextNumber + sent.size();

            database.update(
                    statements.statement("insert-hedge"),
                    hedgeNumber,
                    fundId,
                    thisOne.currency(),
                    thisOne.exposure(),
                    thisOne.suggestedAmount(),
                    chosen,
                    thisOne.instrument(),
                    whoSentIt,
                    "FXM-" + hedgeNumber);

            sent.add(hedgeNumber);
        }
        return sent;
    }

    private long nextHedgeId() {
        Long next = database.queryForObject(statements.statement("select-next-hedge-id"), Long.class);

        return next == null ? 1 : next;
    }
}
