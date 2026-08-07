package vyshaliprabananthlal.stream.send;

import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Component;
import vyshaliprabananthlal.stream.message.MovingHolding;
import vyshaliprabananthlal.stream.plumbing.Pace;
import vyshaliprabananthlal.stream.plumbing.Rows;
import vyshaliprabananthlal.stream.plumbing.SendToKafka;

@Component
public class PositionSender implements Sender {

  private static final String KAFKA_TOPIC = "rtat.position";
  private static final int HOW_MANY_PER_SECOND = 7733;
  private static final int HOW_MANY_TO_LOAD = 200000;

  private static final Random DICE = new Random();

  private final Rows rows;
  private final SendToKafka kafka;

  public PositionSender(Rows rows, SendToKafka kafka) {
    this.rows = rows;
    this.kafka = kafka;
  }

  @Override
  public String name() {
    return "position";
  }

  @Override
  public void sendUntilStopped() throws InterruptedException {
    List<MovingHolding> holdings =
        rows.loadOrComplain(
            "SELECT account_id, product_id, how_many FROM position LIMIT " + HOW_MANY_TO_LOAD,
            (row, number) -> new MovingHolding(row.getInt(1), row.getInt(2), row.getDouble(3)),
            "no positions found - run db/3-generate.sql first");

    Pace pace = new Pace();

    while (!Thread.currentThread().isInterrupted()) {
      MovingHolding holding = holdings.get(DICE.nextInt(holdings.size()));
      holding.moveALittle();

      kafka.send(KAFKA_TOPIC, holding.messageKey(), holding.asMessage());

      pace.waitYourTurn(HOW_MANY_PER_SECOND);
    }
  }
}
