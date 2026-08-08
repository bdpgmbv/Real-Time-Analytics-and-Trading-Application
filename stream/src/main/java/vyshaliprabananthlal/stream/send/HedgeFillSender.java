package vyshaliprabananthlal.stream.send;

import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vyshaliprabananthlal.stream.message.WaitingHedge;
import vyshaliprabananthlal.stream.plumbing.KafkaPublisher;
import vyshaliprabananthlal.stream.plumbing.QueryRunner;
import vyshaliprabananthlal.stream.plumbing.SendRate;

@Component
public class HedgeFillSender implements Sender {

  private static final Logger LOG = LoggerFactory.getLogger(HedgeFillSender.class);

  private static final String KAFKA_TOPIC = "rtat.hedge-fill";

  private static final int HOW_MANY_PER_SECOND = 20;
  private static final int HOW_MANY_TO_LOAD = 20000;
  private static final int ONE_IN_THIS_MANY_IS_SPLIT = 3;
  private static final int LOOK_AGAIN_AFTER_SECONDS = 5;

  private static final Random RANDOM = new Random();

  private final QueryRunner rows;
  private final KafkaPublisher kafka;

  public HedgeFillSender(QueryRunner rows, KafkaPublisher kafka) {
    this.rows = rows;
    this.kafka = kafka;
  }

  @Override
  public String name() {
    return "hedge-fill";
  }

  @Override
  public void sendContinuously() throws InterruptedException {
    long nextFillNumber = System.currentTimeMillis();
    SendRate pace = new SendRate();

    while (!Thread.currentThread().isInterrupted()) {
      List<WaitingHedge> waiting = pendingHedges();

      if (waiting.isEmpty()) {
        LOG.info("nothing waiting to be filled, looking again in a moment");
        Thread.sleep(LOOK_AGAIN_AFTER_SECONDS * 1000L);
        continue;
      }

      for (WaitingHedge hedge : waiting) {
        boolean itComesBackInTwoParts = RANDOM.nextInt(ONE_IN_THIS_MANY_IS_SPLIT) == 0;

        List<String> fills = hedge.fillMessages(nextFillNumber, itComesBackInTwoParts);
        nextFillNumber = nextFillNumber + fills.size();

        for (String fill : fills) {
          kafka.send(KAFKA_TOPIC, hedge.messageKey(), fill);
          pace.acquire(HOW_MANY_PER_SECOND);
        }
      }
    }
  }

  private List<WaitingHedge> pendingHedges() {
    return rows.query(
        "SELECT hedge_id, client_chose, their_reference FROM hedge"
            + " WHERE status IN ('SENT', 'PARTIALLY FILLED')"
            + " ORDER BY hedge_id LIMIT "
            + HOW_MANY_TO_LOAD,
        (row, number) -> new WaitingHedge(row.getLong(1), row.getDouble(2), row.getString(3)));
  }
}
