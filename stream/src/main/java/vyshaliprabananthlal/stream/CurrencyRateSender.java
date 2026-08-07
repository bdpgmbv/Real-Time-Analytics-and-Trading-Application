package vyshaliprabananthlal.stream;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.apache.kafka.clients.producer.KafkaProducer;

public final class CurrencyRateSender {

  private static final String KAFKA_TOPIC = "rtat.fx-rate";
  private static final int HOW_MANY_PER_SECOND = 100;

  private static final Random DICE = new Random();

  private CurrencyRateSender() {}

  public static void main(String[] args) throws Exception {
    List<Rate> rates = loadRatesFromDatabase();

    System.out.println("loaded " + rates.size() + " exchange rates");
    System.out.println("sending " + HOW_MANY_PER_SECOND + " a second to " + KAFKA_TOPIC);

    sendRatesForever(rates);
  }

  private static List<Rate> loadRatesFromDatabase() throws SQLException {
    String askForTheRates = "SELECT from_currency, to_currency, rate FROM fx_rate";
    List<Rate> rates = new ArrayList<>();

    try (Connection database = Database.connect();
        Statement question = database.createStatement();
        ResultSet answer = question.executeQuery(askForTheRates)) {

      while (answer.next()) {
        String from = answer.getString(1).trim();
        String to = answer.getString(2).trim();
        double rate = answer.getDouble(3);

        rates.add(new Rate(from, to, rate));
      }
    }

    if (rates.isEmpty()) {
      throw new IllegalStateException("no rates found - run db/2-reference.sql first");
    }
    return rates;
  }

  private static void sendRatesForever(List<Rate> rates) throws InterruptedException {
    long howManySent = 0;

    try (KafkaProducer<String, String> kafka = Kafka.connect()) {
      while (true) {
        Rate rate = rates.get(DICE.nextInt(rates.size()));
        rate.moveALittle();

        Kafka.send(kafka, KAFKA_TOPIC, rate.messageKey(), rate.asMessage());

        howManySent = howManySent + 1;
        if (howManySent % 500 == 0) {
          System.out.println("sent " + howManySent + " rates");
        }

        Thread.sleep(1000 / HOW_MANY_PER_SECOND);
      }
    }
  }

  private static final class Rate {

    private final String fromCurrency;
    private final String toCurrency;
    private final double startedAt;

    private double rightNow;

    Rate(String fromCurrency, String toCurrency, double startedAt) {
      this.fromCurrency = fromCurrency;
      this.toCurrency = toCurrency;
      this.startedAt = startedAt;
      this.rightNow = startedAt;
    }

    void moveALittle() {
      boolean sameCurrencyBothSides = fromCurrency.equals(toCurrency);
      if (sameCurrencyBothSides) {
        return;
      }

      double tinyRandomPush = (DICE.nextDouble() - 0.5) * 0.0004 * startedAt;
      double pullBackTowardsStart = (startedAt - rightNow) * 0.01;

      rightNow = rightNow + tinyRandomPush + pullBackTowardsStart;
    }

    String messageKey() {
      return fromCurrency + "-" + toCurrency;
    }

    String asMessage() {
      return String.format(
          "{\"from\":\"%s\",\"to\":\"%s\",\"rate\":%s}", fromCurrency, toCurrency, rightNow);
    }
  }
}
