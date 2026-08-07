package vyshaliprabananthlal.stream.send;

import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Component;
import vyshaliprabananthlal.stream.message.MovingPrice;
import vyshaliprabananthlal.stream.plumbing.Pace;
import vyshaliprabananthlal.stream.plumbing.Rows;
import vyshaliprabananthlal.stream.plumbing.SendToKafka;

@Component
public class PriceSender implements Sender {

  private static final String KAFKA_TOPIC = "rtat.price";

  private static final int NORMAL_PER_SECOND = 208;
  private static final int BUSY_PER_SECOND = 4167;
  private static final int A_BUSY_SPELL_ARRIVES_EVERY_SECONDS = 1200;
  private static final int A_BUSY_SPELL_LASTS_SECONDS = 10;

  private static final Random DICE = new Random();

  private final Rows rows;
  private final SendToKafka kafka;

  public PriceSender(Rows rows, SendToKafka kafka) {
    this.rows = rows;
    this.kafka = kafka;
  }

  @Override
  public String name() {
    return "price";
  }

  @Override
  public void sendUntilStopped() throws InterruptedException {
    List<MovingPrice> prices =
        rows.loadOrComplain(
            "SELECT product_id, price FROM price WHERE price > 1",
            (row, number) -> new MovingPrice(row.getInt(1), row.getDouble(2)),
            "no prices found - run db/3-generate.sql first");

    long startedAtSecond = System.currentTimeMillis() / 1000;
    Pace pace = new Pace();

    while (!Thread.currentThread().isInterrupted()) {
      MovingPrice price = prices.get(DICE.nextInt(prices.size()));
      price.moveALittle();

      kafka.send(KAFKA_TOPIC, price.messageKey(), price.asMessage());

      pace.waitYourTurn(inABusySpell(startedAtSecond) ? BUSY_PER_SECOND : NORMAL_PER_SECOND);
    }
  }

  private static boolean inABusySpell(long startedAtSecond) {
    long secondsRunning = System.currentTimeMillis() / 1000 - startedAtSecond;

    return secondsRunning % A_BUSY_SPELL_ARRIVES_EVERY_SECONDS < A_BUSY_SPELL_LASTS_SECONDS;
  }
}
