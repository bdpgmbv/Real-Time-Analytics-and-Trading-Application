package vyshaliprabananthlal.jobs.exposure;

import java.util.List;
import java.util.Map;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

public class PriceDeltaFunction extends KeyedProcessFunction<Integer, PriceTick, ExposureDelta> {

  private static final long serialVersionUID = 1L;

  private final Map<Integer, List<FundHolding>> whoHoldsWhat;

  private transient ValueState<Double> whatItWasWorthLastTime;

  public PriceDeltaFunction(Map<Integer, List<FundHolding>> whoHoldsWhat) {
    this.whoHoldsWhat = whoHoldsWhat;
  }

  @Override
  public void open(OpenContext context) {
    whatItWasWorthLastTime =
        getRuntimeContext()
            .getState(new ValueStateDescriptor<>("the price we saw last", Double.class));
  }

  @Override
  public void processElement(PriceTick tick, Context context, Collector<ExposureDelta> out)
      throws Exception {

    List<FundHolding> holders = whoHoldsWhat.get(tick.productId());

    Double lastPrice = whatItWasWorthLastTime.value();
    whatItWasWorthLastTime.update(tick.price());

    if (holders == null || holders.isEmpty()) {
      return;
    }

    if (lastPrice == null) {
      sendOut(holders, tick.price(), out);
      return;
    }

    double howMuchThePriceMoved = tick.price() - lastPrice;
    if (howMuchThePriceMoved == 0) {
      return;
    }

    sendOut(holders, howMuchThePriceMoved, out);
  }

  private void sendOut(
      List<FundHolding> holders, double moveOrWholePrice, Collector<ExposureDelta> out) {
    for (FundHolding holder : holders) {
      out.collect(
          new ExposureDelta(
              holder.fundId(), holder.currency(), moveOrWholePrice * holder.howMany()));
    }
  }

  static Configuration noSettings() {
    return new Configuration();
  }
}
