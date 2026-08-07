package vyshaliprabananthlal.stream.send;

import java.util.List;
import java.util.Random;
import org.apache.kafka.clients.producer.KafkaProducer;
import vyshaliprabananthlal.stream.message.MovingPrice;
import vyshaliprabananthlal.stream.plumbing.Kafka;
import vyshaliprabananthlal.stream.plumbing.Pace;
import vyshaliprabananthlal.stream.plumbing.Rows;

public final class PriceSender {

  private static final String KAFKA_TOPIC = "rtat.price";

  private static final int NORMAL_PER_SECOND = 208;
  private static final int BUSY_PER_SECOND = 4167;
  private static final int A_BUSY_SPELL_ARRIVES_EVERY_SECONDS = 1200;
  private static final int A_BUSY_SPELL_LASTS_SECONDS = 10;

  private static final Random DICE = new Random();

  private PriceSender() {}

  public static void main(String[] args) throws Exception {
    List<MovingPrice> prices =
        Rows.loadOrComplain(
            "SELECT product_id, price FROM price WHERE price > 1",
            row -> new MovingPrice(row.getInt(1), row.getDouble(2)),
            "no prices found - run db/3-generate.sql first");

    System.out.println("loaded " + prices.size() + " prices");
    System.out.println("sending " + NORMAL_PER_SECOND + " a second to " + KAFKA_TOPIC);
    System.out.println("every 20 minutes it speeds up to " + BUSY_PER_SECOND + " a second");

    sendPricesForever(prices);
  }

  private static void sendPricesForever(List<MovingPrice> prices) throws InterruptedException {
    long startedAtSecond = System.currentTimeMillis() / 1000;
    long howManySent = 0;

    Pace pace = new Pace();

    try (KafkaProducer<String, String> kafka = Kafka.connect()) {
      while (true) {
        boolean busy = inABusySpell(startedAtSecond);

        MovingPrice price = prices.get(DICE.nextInt(prices.size()));
        price.moveALittle();

        Kafka.send(kafka, KAFKA_TOPIC, price.messageKey(), price.asMessage());

        howManySent = howManySent + 1;
        if (howManySent % 2000 == 0) {
          System.out.println("sent " + howManySent + " prices" + (busy ? "   BUSY" : ""));
        }

        pace.waitYourTurn(busy ? BUSY_PER_SECOND : NORMAL_PER_SECOND);
      }
    }
  }

  private static boolean inABusySpell(long startedAtSecond) {
    long secondsRunning = System.currentTimeMillis() / 1000 - startedAtSecond;
    long whereWeAreInTheCycle = secondsRunning % A_BUSY_SPELL_ARRIVES_EVERY_SECONDS;

    return whereWeAreInTheCycle < A_BUSY_SPELL_LASTS_SECONDS;
  }
}
