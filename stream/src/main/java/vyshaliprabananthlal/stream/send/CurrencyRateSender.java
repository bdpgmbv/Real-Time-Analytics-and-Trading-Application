package vyshaliprabananthlal.stream.send;

import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Component;
import vyshaliprabananthlal.stream.message.MovingRate;
import vyshaliprabananthlal.stream.plumbing.KafkaPublisher;
import vyshaliprabananthlal.stream.plumbing.QueryRunner;
import vyshaliprabananthlal.stream.plumbing.SendRate;

@Component
public class CurrencyRateSender implements Sender {

  private static final String KAFKA_TOPIC = "rtat.fx-rate";
  private static final int HOW_MANY_PER_SECOND = 100;

  private static final Random RANDOM = new Random();

  private final QueryRunner rows;
  private final KafkaPublisher kafka;

  public CurrencyRateSender(QueryRunner rows, KafkaPublisher kafka) {
    this.rows = rows;
    this.kafka = kafka;
  }

  @Override
  public String name() {
    return "rate";
  }

  @Override
  public void sendContinuously() throws InterruptedException {
    List<MovingRate> rates =
        rows.queryRequired(
            "SELECT from_currency, to_currency, rate FROM fx_rate",
            (row, number) ->
                new MovingRate(row.getString(1).trim(), row.getString(2).trim(), row.getDouble(3)),
            "no rates found - run db/2-reference.sql first");

    SendRate pace = new SendRate();

    while (!Thread.currentThread().isInterrupted()) {
      MovingRate rate = rates.get(RANDOM.nextInt(rates.size()));
      rate.move();

      kafka.send(KAFKA_TOPIC, rate.messageKey(), rate.asMessage());

      pace.acquire(HOW_MANY_PER_SECOND);
    }
  }
}
