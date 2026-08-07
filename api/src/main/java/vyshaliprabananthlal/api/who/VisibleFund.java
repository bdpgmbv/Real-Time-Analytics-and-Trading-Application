package vyshaliprabananthlal.api.who;

public record VisibleFund(
    int fundId, String name, String reportingCurrency, boolean canSendTrades) {}
