package vyshaliprabananthlal.stream;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.apache.kafka.clients.producer.KafkaProducer;

public final class PriceSender {

  private static final String KAFKA_TOPIC = "rtat.price";

  private static final int NORMAL_PER_SECOND = 208;
  private static final int BUSY_PER_SECOND = 4167;
  private static final int A_BUSY_SPELL_ARRIVES_EVERY_SECONDS = 1200;
  private static final int A_BUSY_SPELL_LASTS_SECONDS = 10;

  private static final Random DICE = new Random();

  private PriceSender() {}

  public static void main(String[] args) throws Exception {
    List<Quote> quotes = loadPricesFromDatabase();

    System.out.println("loaded " + quotes.size() + " prices");
    System.out.println("sending " + NORMAL_PER_SECOND + " a second to " + KAFKA_TOPIC);
    System.out.println("every 20 minutes it speeds up to " + BUSY_PER_SECOND + " a second");

    sendPricesForever(quotes);
  }

  private static List<Quote> loadPricesFromDatabase() throws SQLException {
    String askForThePrices = "SELECT product_id, price FROM price WHERE price > 1";
    List<Quote> quotes = new ArrayList<>();

    try (Connection database = Database.connect();
        Statement question = database.createStatement();
        ResultSet answer = question.executeQuery(askForThePrices)) {

      while (answer.next()) {
        int product = answer.getInt(1);
        double price = answer.getDouble(2);

        quotes.add(new Quote(product, price));
      }
    }

    if (quotes.isEmpty()) {
      throw new IllegalStateException("no prices found - run db/3-generate.sql first");
    }
    return quotes;
  }

  private static void sendPricesForever(List<Quote> quotes) throws InterruptedException {
    long startedAtSecond = System.currentTimeMillis() / 1000;
    long howManySent = 0;

    try (KafkaProducer<String, String> kafka = Kafka.connect()) {
      while (true) {
        boolean busy = inABusySpell(startedAtSecond);
        int sendThisManyPerSecond = busy ? BUSY_PER_SECOND : NORMAL_PER_SECOND;

        Quote quote = quotes.get(DICE.nextInt(quotes.size()));
        quote.moveALittle();

        Kafka.send(kafka, KAFKA_TOPIC, quote.messageKey(), quote.asMessage());

        howManySent = howManySent + 1;
        if (howManySent % 2000 == 0) {
          System.out.println("sent " + howManySent + " prices" + (busy ? "   BUSY" : ""));
        }

        Thread.sleep(1000 / sendThisManyPerSecond);
      }
    }
  }

  private static boolean inABusySpell(long startedAtSecond) {
    long secondsRunning = System.currentTimeMillis() / 1000 - startedAtSecond;
    long whereWeAreInTheCycle = secondsRunning % A_BUSY_SPELL_ARRIVES_EVERY_SECONDS;

    return whereWeAreInTheCycle < A_BUSY_SPELL_LASTS_SECONDS;
  }

  private static final class Quote {

    private final int productNumber;
    private final double startedAt;

    private double rightNow;

    Quote(int productNumber, double startedAt) {
      this.productNumber = productNumber;
      this.startedAt = startedAt;
      this.rightNow = startedAt;
    }

    void moveALittle() {
      double smallMove = (DICE.nextDouble() - 0.5) * 0.01 * startedAt;
      double pullBackTowardsStart = (startedAt - rightNow) * 0.005;

      rightNow = Math.max(0.01, rightNow + smallMove + pullBackTowardsStart);
    }

    String messageKey() {
      return Integer.toString(productNumber);
    }

    String asMessage() {
      double rounded = Math.round(rightNow * 1000000) / 1000000.0;

      return String.format(
          "{\"productId\":%d,\"price\":%s,\"howFresh\":\"DELAYED 20 MINUTES\"}",
          productNumber, rounded);
    }
  }
}
