package vyshaliprabananthlal.jobs.exposure;

import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

public class RunningTotalFunction
        extends KeyedProcessFunction<String, ExposureMessages.ExposureDelta, ExposureMessages.RunningTotal> {

    private static final long serialVersionUID = 1L;

    private transient ValueState<Double> total;

    @Override
    public void open(OpenContext context) {
        total = getRuntimeContext().getState(new ValueStateDescriptor<>("exposure so far", Double.class));
    }

    @Override
    public void processElement(
            ExposureMessages.ExposureDelta delta, Context context, Collector<ExposureMessages.RunningTotal> out)
            throws Exception {

        double soFar = total.value() == null ? 0.0 : total.value();
        double now = soFar + delta.changeBy();

        total.update(now);

        out.collect(new ExposureMessages.RunningTotal(delta.fundId(), delta.currency(), now));
    }
}
