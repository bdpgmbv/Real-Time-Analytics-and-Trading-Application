package vyshaliprabananthlal.stream.send;

import java.util.List;
import java.util.Random;
import org.apache.kafka.clients.producer.KafkaProducer;
import vyshaliprabananthlal.stream.message.MovingHolding;
import vyshaliprabananthlal.stream.plumbing.Kafka;
import vyshaliprabananthlal.stream.plumbing.Pace;
import vyshaliprabananthlal.stream.plumbing.Rows;

public final class PositionSender {

  private static final String KAFKA_TOPIC = "rtat.position";
  private static final int HOW_MANY_PER_SECOND = 7733;
  private static final int HOW_MANY_TO_LOAD = 200000;

  private static final Random DICE = new Random();

  private PositionSender() {}

  public static void main(String[] args) throws Exception {
    List<MovingHolding> holdings =
        Rows.loadOrComplain(
            "SELECT account_id, product_id, how_many FROM position LIMIT " + HOW_MANY_TO_LOAD,
            row -> new MovingHolding(row.getInt(1), row.getInt(2), row.getDouble(3)),
            "no positions found - run db/3-generate.sql first");

    System.out.println("loaded " + holdings.size() + " positions");
    System.out.println("sending " + HOW_MANY_PER_SECOND + " a second to " + KAFKA_TOPIC);

    sendPositionsForever(holdings);
  }

  private static void sendPositionsForever(List<MovingHolding> holdings)
      throws InterruptedException {

    long howManySent = 0;
    Pace pace = new Pace();

    try (KafkaProducer<String, String> kafka = Kafka.connect()) {
      while (true) {
        MovingHolding holding = holdings.get(DICE.nextInt(holdings.size()));
        holding.moveALittle();

        Kafka.send(kafka, KAFKA_TOPIC, holding.messageKey(), holding.asMessage());

        howManySent = howManySent + 1;
        if (howManySent % 20000 == 0) {
          System.out.println("sent " + howManySent + " position updates");
        }

        pace.waitYourTurn(HOW_MANY_PER_SECOND);
      }
    }
  }
}
