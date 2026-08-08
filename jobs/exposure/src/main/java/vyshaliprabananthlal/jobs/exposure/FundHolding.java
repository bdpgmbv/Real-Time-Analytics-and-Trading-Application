package vyshaliprabananthlal.jobs.exposure;

import java.io.Serializable;

public record FundHolding(int fundId, String currency, double howMany) implements Serializable {}
