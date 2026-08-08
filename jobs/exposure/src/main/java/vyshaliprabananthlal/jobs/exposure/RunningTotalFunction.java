package vyshaliprabananthlal.jobs.exposure;

import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import vyshaliprabananthlal.jobs.exposure.ExposureMessages.ExposureDelta;
import vyshaliprabananthlal.jobs.exposure.ExposureMessages.RunningTotal;

/**
 * Adds the differences up, one running total per fund and currency.
 *
 * <p>Held in the security's own currency, never converted. That is what makes an FX rate move
 * free: the stored number has not changed, only the rate it is multiplied by when somebody reads
 * it. Converting here instead would mean rewriting every total each time a rate ticked.
 *
 * <p>Flink checkpoints the total, so a restart carries on from where it was rather than from zero.
 */
public class RunningTotalFunction extends KeyedProcessFunction<String, ExposureDelta, RunningTotal> {

    private static final long serialVersionUID = 1L;

    private transient ValueState<Double> total;

    @Override
    public void open(OpenContext context) {
        total = getRuntimeContext().getState(new ValueStateDescriptor<>("exposureTotal", Double.class));
    }

    @Override
    public void processElement(ExposureDelta delta, Context context, Collector<RunningTotal> out) throws Exception {
        double before = total.value() == null ? 0.0 : total.value();
        double after = before + delta.changeBy();

        total.update(after);

        out.collect(new RunningTotal(delta.fundId(), delta.currency(), after));
    }
}
