package vyshaliprabananthlal.jobs.exposure;

import java.io.Serializable;

/**
 * Everything the job passes from one step to the next.
 *
 * <p>They live together rather than in four files because they only make sense together: a
 * PriceTick plus the FundHoldings for that security produce ExposureDeltas, which add up into
 * RunningTotals. Reading them in order is reading what the job does.
 *
 * <p>They cannot be nested inside the functions that produce them, because Java will not let a
 * class name a nested type of its own in its implements clause.
 */
public final class ExposureMessages {

    private ExposureMessages() {}

    /** One price, as it arrived on the topic. */
    public record PriceTick(int productId, double price) implements Serializable {}

    /** One fund's total holding of one security, and the currency that security trades in. */
    public record FundHolding(int fundId, String currency, double quantity) implements Serializable {}

    /** How much one fund's exposure to one currency moved. The difference, never the whole value. */
    public record ExposureDelta(int fundId, String currency, double changeBy) implements Serializable {

        public String key() {
            return fundId + "|" + currency;
        }
    }

    /** A fund's exposure to one currency, as it stands now. */
    public record RunningTotal(int fundId, String currency, double total) implements Serializable {

        public String asMessage() {
            return String.format(
                    "{\"fundId\":%d,\"currency\":\"%s\",\"exposure\":%s}",
                    fundId, currency, Math.round(total * 10000) / 10000.0);
        }
    }
}
