package vyshaliprabananthlal.stream.send;

import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Component;
import vyshaliprabananthlal.stream.message.WhatToTrade;
import vyshaliprabananthlal.stream.plumbing.Pace;
import vyshaliprabananthlal.stream.plumbing.Rows;
import vyshaliprabananthlal.stream.plumbing.SendToKafka;

@Component
public class TradeSender implements Sender {

  private static final String KAFKA_TOPIC = "rtat.trade";
  private static final int HOW_MANY_PER_SECOND = 8;
  private static final int HOW_MANY_TO_LOAD = 100000;

  private static final Random DICE = new Random();

  private final Rows rows;
  private final SendToKafka kafka;

  public TradeSender(Rows rows, SendToKafka kafka) {
    this.rows = rows;
    this.kafka = kafka;
  }

  @Override
  public String name() {
    return "trade";
  }

  @Override
  public void sendUntilStopped() throws InterruptedException {
    List<WhatToTrade> choices =
        rows.loadOrComplain(
            "SELECT account_id, product_id FROM position LIMIT " + HOW_MANY_TO_LOAD,
            (row, number) -> new WhatToTrade(row.getInt(1), row.getInt(2)),
            "no positions found - run db/3-generate.sql first");

    long nextTradeNumber = System.currentTimeMillis();
    Pace pace = new Pace();

    while (!Thread.currentThread().isInterrupted()) {
      WhatToTrade choice = choices.get(DICE.nextInt(choices.size()));

      kafka.send(KAFKA_TOPIC, choice.messageKey(), choice.newTrade(nextTradeNumber));

      nextTradeNumber = nextTradeNumber + 1;
      pace.waitYourTurn(HOW_MANY_PER_SECOND);
    }
  }
}
