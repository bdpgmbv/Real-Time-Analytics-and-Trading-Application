package vyshaliprabananthlal.jobs.exposure;

import java.io.Serializable;

public record WhoHoldsIt(int fundId, String currency, double howMany) implements Serializable {}
