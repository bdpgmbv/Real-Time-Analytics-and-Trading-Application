package vyshaliprabananthlal.jobs.exposure;

import java.util.List;
import java.util.Map;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import vyshaliprabananthlal.jobs.exposure.ExposureMessages.ExposureDelta;
import vyshaliprabananthlal.jobs.exposure.ExposureMessages.FundHolding;
import vyshaliprabananthlal.jobs.exposure.ExposureMessages.PriceTick;

/**
 * Turns a price tick into the change it makes to each fund holding that security.
 *
 * <p>The difference, never the whole value. Airbus going from 100 to 110 moves a fund holding six
 * of them by 60, and saying so costs one addition per holder. Recalculating instead would mean
 * revaluing every position the fund owns to find the same number.
 *
 * <p>Keyed by product, so the last price for one security is kept in one place and Flink restores
 * it after a restart. Without that state a restart would send the whole value again and double
 * every total.
 */
public class PriceDeltaFunction extends KeyedProcessFunction<Integer, PriceTick, ExposureDelta> {

    private static final long serialVersionUID = 1L;

    /** Which funds hold each security, and how much. Read once when the job starts. */
    private final Map<Integer, List<FundHolding>> holdingsByProduct;

    private transient ValueState<Double> lastPrice;

    public PriceDeltaFunction(Map<Integer, List<FundHolding>> holdingsByProduct) {
        this.holdingsByProduct = holdingsByProduct;
    }

    @Override
    public void open(OpenContext context) {
        lastPrice = getRuntimeContext().getState(new ValueStateDescriptor<>("lastPrice", Double.class));
    }

    @Override
    public void processElement(PriceTick tick, Context context, Collector<ExposureDelta> out) throws Exception {
        List<FundHolding> holders = holdingsByProduct.get(tick.productId());

        Double previousPrice = lastPrice.value();
        lastPrice.update(tick.price());

        // A security nobody holds costs nothing beyond remembering its price.
        if (holders == null || holders.isEmpty()) {
            return;
        }

        // The first price we ever see has nothing to compare against, so it is the whole value.
        if (previousPrice == null) {
            emitPerHolder(holders, tick.price(), out);
            return;
        }

        double priceMove = tick.price() - previousPrice;
        if (priceMove == 0) {
            return;
        }

        emitPerHolder(holders, priceMove, out);
    }

    /** @param amountPerUnit the price move, or the whole price the first time we see this security */
    private void emitPerHolder(List<FundHolding> holders, double amountPerUnit, Collector<ExposureDelta> out) {
        for (FundHolding holder : holders) {
            out.collect(new ExposureDelta(holder.fundId(), holder.currency(), amountPerUnit * holder.quantity()));
        }
    }
}
