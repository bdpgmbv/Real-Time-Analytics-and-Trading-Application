package vyshaliprabananthlal.stream.send;

import java.util.List;
import java.util.Random;
import org.apache.kafka.clients.producer.KafkaProducer;
import vyshaliprabananthlal.stream.message.MovingRate;
import vyshaliprabananthlal.stream.plumbing.Kafka;
import vyshaliprabananthlal.stream.plumbing.Pace;
import vyshaliprabananthlal.stream.plumbing.Rows;

public final class CurrencyRateSender {

  private static final String KAFKA_TOPIC = "rtat.fx-rate";
  private static final int HOW_MANY_PER_SECOND = 100;

  private static final Random DICE = new Random();

  private CurrencyRateSender() {}

  public static void main(String[] args) throws Exception {
    List<MovingRate> rates =
        Rows.loadOrComplain(
            "SELECT from_currency, to_currency, rate FROM fx_rate",
            row ->
                new MovingRate(row.getString(1).trim(), row.getString(2).trim(), row.getDouble(3)),
            "no rates found - run db/2-reference.sql first");

    System.out.println("loaded " + rates.size() + " exchange rates");
    System.out.println("sending " + HOW_MANY_PER_SECOND + " a second to " + KAFKA_TOPIC);

    sendRatesForever(rates);
  }

  private static void sendRatesForever(List<MovingRate> rates) throws InterruptedException {
    long howManySent = 0;
    Pace pace = new Pace();

    try (KafkaProducer<String, String> kafka = Kafka.connect()) {
      while (true) {
        MovingRate rate = rates.get(DICE.nextInt(rates.size()));
        rate.moveALittle();

        Kafka.send(kafka, KAFKA_TOPIC, rate.messageKey(), rate.asMessage());

        howManySent = howManySent + 1;
        if (howManySent % 500 == 0) {
          System.out.println("sent " + howManySent + " rates");
        }

        pace.waitYourTurn(HOW_MANY_PER_SECOND);
      }
    }
  }
}
