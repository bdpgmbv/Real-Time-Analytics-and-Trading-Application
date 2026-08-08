package vyshaliprabananthlal.stream.send;

import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Component;
import vyshaliprabananthlal.stream.message.MovingHolding;
import vyshaliprabananthlal.stream.plumbing.KafkaPublisher;
import vyshaliprabananthlal.stream.plumbing.QueryRunner;
import vyshaliprabananthlal.stream.plumbing.SendRate;

@Component
public class PositionSender implements Sender {

  private static final String KAFKA_TOPIC = "rtat.position";
  private static final int HOW_MANY_PER_SECOND = 7733;
  private static final int HOW_MANY_TO_LOAD = 200000;

  private static final Random RANDOM = new Random();

  private final QueryRunner rows;
  private final KafkaPublisher kafka;

  public PositionSender(QueryRunner rows, KafkaPublisher kafka) {
    this.rows = rows;
    this.kafka = kafka;
  }

  @Override
  public String name() {
    return "position";
  }

  @Override
  public void sendContinuously() throws InterruptedException {
    List<MovingHolding> holdings =
        rows.queryRequired(
            "SELECT account_id, product_id, quantity FROM position LIMIT " + HOW_MANY_TO_LOAD,
            (row, number) -> new MovingHolding(row.getInt(1), row.getInt(2), row.getDouble(3)),
            "no positions found - run db/3-generate.sql first");

    SendRate pace = new SendRate();

    while (!Thread.currentThread().isInterrupted()) {
      MovingHolding holding = holdings.get(RANDOM.nextInt(holdings.size()));
      holding.move();

      kafka.send(KAFKA_TOPIC, holding.messageKey(), holding.asMessage());

      pace.acquire(HOW_MANY_PER_SECOND);
    }
  }
}
