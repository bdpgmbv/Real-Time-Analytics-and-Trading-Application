package vyshaliprabananthlal.stream.send;

import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Component;
import vyshaliprabananthlal.stream.message.TradeCandidate;
import vyshaliprabananthlal.stream.plumbing.KafkaPublisher;
import vyshaliprabananthlal.stream.plumbing.QueryRunner;
import vyshaliprabananthlal.stream.plumbing.SendRate;

@Component
public class TradeSender implements Sender {

  private static final String KAFKA_TOPIC = "rtat.trade";
  private static final int HOW_MANY_PER_SECOND = 8;
  private static final int HOW_MANY_TO_LOAD = 100000;

  private static final Random DICE = new Random();

  private final QueryRunner rows;
  private final KafkaPublisher kafka;

  public TradeSender(QueryRunner rows, KafkaPublisher kafka) {
    this.rows = rows;
    this.kafka = kafka;
  }

  @Override
  public String name() {
    return "trade";
  }

  @Override
  public void sendUntilStopped() throws InterruptedException {
    List<TradeCandidate> choices =
        rows.loadOrComplain(
            "SELECT account_id, product_id FROM position LIMIT " + HOW_MANY_TO_LOAD,
            (row, number) -> new TradeCandidate(row.getInt(1), row.getInt(2)),
            "no positions found - run db/3-generate.sql first");

    long nextTradeNumber = System.currentTimeMillis();
    SendRate pace = new SendRate();

    while (!Thread.currentThread().isInterrupted()) {
      TradeCandidate choice = choices.get(DICE.nextInt(choices.size()));

      kafka.send(KAFKA_TOPIC, choice.messageKey(), choice.newTrade(nextTradeNumber));

      nextTradeNumber = nextTradeNumber + 1;
      pace.waitYourTurn(HOW_MANY_PER_SECOND);
    }
  }
}
