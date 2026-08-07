package vyshaliprabananthlal.stream.send;

import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Component;
import vyshaliprabananthlal.stream.message.MovingRate;
import vyshaliprabananthlal.stream.plumbing.Pace;
import vyshaliprabananthlal.stream.plumbing.Rows;
import vyshaliprabananthlal.stream.plumbing.SendToKafka;

@Component
public class CurrencyRateSender implements Sender {

  private static final String KAFKA_TOPIC = "rtat.fx-rate";
  private static final int HOW_MANY_PER_SECOND = 100;

  private static final Random DICE = new Random();

  private final Rows rows;
  private final SendToKafka kafka;

  public CurrencyRateSender(Rows rows, SendToKafka kafka) {
    this.rows = rows;
    this.kafka = kafka;
  }

  @Override
  public String name() {
    return "rate";
  }

  @Override
  public void sendUntilStopped() throws InterruptedException {
    List<MovingRate> rates =
        rows.loadOrComplain(
            "SELECT from_currency, to_currency, rate FROM fx_rate",
            (row, number) ->
                new MovingRate(row.getString(1).trim(), row.getString(2).trim(), row.getDouble(3)),
            "no rates found - run db/2-reference.sql first");

    Pace pace = new Pace();

    while (!Thread.currentThread().isInterrupted()) {
      MovingRate rate = rates.get(DICE.nextInt(rates.size()));
      rate.moveALittle();

      kafka.send(KAFKA_TOPIC, rate.messageKey(), rate.asMessage());

      pace.waitYourTurn(HOW_MANY_PER_SECOND);
    }
  }
}
