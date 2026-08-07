package vyshaliprabananthlal.stream.send;

import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vyshaliprabananthlal.stream.message.WaitingHedge;
import vyshaliprabananthlal.stream.plumbing.Pace;
import vyshaliprabananthlal.stream.plumbing.Rows;
import vyshaliprabananthlal.stream.plumbing.SendToKafka;

@Component
public class HedgeFillSender implements Sender {

  private static final Logger LOG = LoggerFactory.getLogger(HedgeFillSender.class);

  private static final String KAFKA_TOPIC = "rtat.hedge-fill";

  private static final int HOW_MANY_PER_SECOND = 20;
  private static final int HOW_MANY_TO_LOAD = 20000;
  private static final int ONE_IN_THIS_MANY_IS_SPLIT = 3;
  private static final int LOOK_AGAIN_AFTER_SECONDS = 5;

  private static final Random DICE = new Random();

  private final Rows rows;
  private final SendToKafka kafka;

  public HedgeFillSender(Rows rows, SendToKafka kafka) {
    this.rows = rows;
    this.kafka = kafka;
  }

  @Override
  public String name() {
    return "hedge-fill";
  }

  @Override
  public void sendUntilStopped() throws InterruptedException {
    long nextFillNumber = System.currentTimeMillis();
    Pace pace = new Pace();

    while (!Thread.currentThread().isInterrupted()) {
      List<WaitingHedge> waiting = whatIsWaiting();

      if (waiting.isEmpty()) {
        LOG.info("nothing waiting to be filled, looking again in a moment");
        Thread.sleep(LOOK_AGAIN_AFTER_SECONDS * 1000L);
        continue;
      }

      for (WaitingHedge hedge : waiting) {
        boolean itComesBackInTwoParts = DICE.nextInt(ONE_IN_THIS_MANY_IS_SPLIT) == 0;

        List<String> fills = hedge.fillMessages(nextFillNumber, itComesBackInTwoParts);
        nextFillNumber = nextFillNumber + fills.size();

        for (String fill : fills) {
          kafka.send(KAFKA_TOPIC, hedge.messageKey(), fill);
          pace.waitYourTurn(HOW_MANY_PER_SECOND);
        }
      }
    }
  }

  private List<WaitingHedge> whatIsWaiting() {
    return rows.loadOrEmpty(
        "SELECT hedge_id, client_chose, their_reference FROM hedge"
            + " WHERE status IN ('SENT', 'PARTIALLY FILLED')"
            + " ORDER BY hedge_id LIMIT "
            + HOW_MANY_TO_LOAD,
        (row, number) -> new WaitingHedge(row.getLong(1), row.getDouble(2), row.getString(3)));
  }
}
