package vyshaliprabananthlal.stream.send;

import java.util.List;
import java.util.Random;
import org.apache.kafka.clients.producer.KafkaProducer;
import vyshaliprabananthlal.stream.message.WaitingHedge;
import vyshaliprabananthlal.stream.plumbing.Kafka;
import vyshaliprabananthlal.stream.plumbing.Pace;
import vyshaliprabananthlal.stream.plumbing.Rows;

public final class HedgeFillSender {

  private static final String KAFKA_TOPIC = "rtat.hedge-fill";

  private static final int HOW_MANY_PER_SECOND = 20;
  private static final int HOW_MANY_TO_LOAD = 20000;
  private static final int ONE_IN_THIS_MANY_IS_SPLIT = 3;

  private static final Random DICE = new Random();

  private HedgeFillSender() {}

  public static void main(String[] args) throws Exception {
    List<WaitingHedge> waiting =
        Rows.loadOrComplain(
            "SELECT hedge_id, client_chose, their_reference FROM hedge"
                + " WHERE status IN ('SENT', 'PARTIALLY FILLED')"
                + " ORDER BY hedge_id LIMIT "
                + HOW_MANY_TO_LOAD,
            row -> new WaitingHedge(row.getLong(1), row.getDouble(2), row.getString(3)),
            "no hedges are waiting to be filled");

    System.out.println("loaded " + waiting.size() + " hedges waiting to be filled");
    System.out.println("sending " + HOW_MANY_PER_SECOND + " fills a second to " + KAFKA_TOPIC);
    System.out.println("one in " + ONE_IN_THIS_MANY_IS_SPLIT + " comes back as two part fills");

    sendFills(waiting);
  }

  private static void sendFills(List<WaitingHedge> waiting) throws InterruptedException {
    long nextFillNumber = System.currentTimeMillis();
    long howManySent = 0;

    Pace pace = new Pace();

    try (KafkaProducer<String, String> kafka = Kafka.connect()) {
      for (WaitingHedge hedge : waiting) {
        boolean itComesBackInTwoParts = DICE.nextInt(ONE_IN_THIS_MANY_IS_SPLIT) == 0;

        List<String> fills = hedge.fillMessages(nextFillNumber, itComesBackInTwoParts);
        nextFillNumber = nextFillNumber + fills.size();

        for (String fill : fills) {
          Kafka.send(kafka, KAFKA_TOPIC, hedge.messageKey(), fill);

          howManySent = howManySent + 1;
          if (howManySent % 100 == 0) {
            System.out.println("sent " + howManySent + " fills");
          }

          pace.waitYourTurn(HOW_MANY_PER_SECOND);
        }
      }
    }

    System.out.println("sent " + howManySent + " fills, nothing left waiting");
  }
}
