package vyshaliprabananthlal.api.who;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vyshaliprabananthlal.calculate.sql.Sql;

@Service
public class Entitlements {

    private final JdbcTemplate database;
    private final Sql sql;

    public Entitlements(JdbcTemplate database, Sql sql) {
        this.database = database;
        this.sql = sql;
    }

    public List<Entitlements.VisibleFund> fundsVisibleTo(String userId) {
        return database.query(
                sql.statement("funds-this-user-may-see"),
                (row, number) -> new Entitlements.VisibleFund(
                        row.getInt(1), row.getString(2), row.getString(3).trim(), row.getBoolean(4)),
                userId);
    }

    public void requireVisible(String userId, int fundId) {
        if (findEntitlement(userId, fundId).isEmpty()) {
            throw new Entitlements.NotAllowed("you cannot see fund " + fundId);
        }
    }

    public void requireTradePermission(String userId, int fundId) {
        List<Boolean> found = findEntitlement(userId, fundId);

        if (found.isEmpty()) {
            throw new Entitlements.NotAllowed("you cannot see fund " + fundId);
        }
        if (!found.get(0)) {
            throw new Entitlements.NotAllowed("you may look at fund " + fundId + " but not send trades for it");
        }
    }

    public List<Integer> filterVisible(String userId, int fundId, List<Integer> asked) {
        if (asked.isEmpty()) {
            return List.of();
        }

        return database.queryForList(
                sql.statement("accounts-this-user-may-see"),
                Integer.class,
                userId,
                fundId,
                asked.toArray(new Integer[0]));
    }

    private List<Boolean> findEntitlement(String userId, int fundId) {
        return database.queryForList(sql.statement("may-this-user-see-this-fund"), Boolean.class, userId, fundId);
    }

    /** A fund this user may open, and whether they may act on it. */
    public record VisibleFund(int fundId, String name, String reportingCurrency, boolean canSendTrades) {}

    /** Refused. Carries no detail about what exists, so probing learns nothing. */
    public static class NotAllowed extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public NotAllowed(String whatWasRefused) {
            super(whatWasRefused);
        }
    }
}
