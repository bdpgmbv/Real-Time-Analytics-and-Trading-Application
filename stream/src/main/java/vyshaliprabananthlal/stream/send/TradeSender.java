package vyshaliprabananthlal.stream.send;

import java.util.List;
import java.util.Random;
import org.apache.kafka.clients.producer.KafkaProducer;
import vyshaliprabananthlal.stream.message.WhatToTrade;
import vyshaliprabananthlal.stream.plumbing.Kafka;
import vyshaliprabananthlal.stream.plumbing.Pace;
import vyshaliprabananthlal.stream.plumbing.Rows;

public final class TradeSender {

  private static final String KAFKA_TOPIC = "rtat.trade";
  private static final int HOW_MANY_PER_SECOND = 8;
  private static final int HOW_MANY_TO_LOAD = 100000;

  private static final Random DICE = new Random();

  private TradeSender() {}

  public static void main(String[] args) throws Exception {
    List<WhatToTrade> choices =
        Rows.loadOrComplain(
            "SELECT account_id, product_id FROM position LIMIT " + HOW_MANY_TO_LOAD,
            row -> new WhatToTrade(row.getInt(1), row.getInt(2)),
            "no positions found - run db/3-generate.sql first");

    System.out.println("loaded " + choices.size() + " account and product pairs");
    System.out.println("sending " + HOW_MANY_PER_SECOND + " trades a second to " + KAFKA_TOPIC);

    sendTradesForever(choices);
  }

  private static void sendTradesForever(List<WhatToTrade> choices) throws InterruptedException {
    long nextTradeNumber = System.currentTimeMillis();
    long howManySent = 0;

    Pace pace = new Pace();

    try (KafkaProducer<String, String> kafka = Kafka.connect()) {
      while (true) {
        WhatToTrade choice = choices.get(DICE.nextInt(choices.size()));

        Kafka.send(kafka, KAFKA_TOPIC, choice.messageKey(), choice.newTrade(nextTradeNumber));

        nextTradeNumber = nextTradeNumber + 1;
        howManySent = howManySent + 1;
        if (howManySent % 20 == 0) {
          System.out.println("sent " + howManySent + " trades");
        }

        pace.waitYourTurn(HOW_MANY_PER_SECOND);
      }
    }
  }
}
