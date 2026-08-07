package vyshaliprabananthlal.stream;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.apache.kafka.clients.producer.KafkaProducer;

public final class PositionSender {

  private static final String KAFKA_TOPIC = "rtat.position";
  private static final int HOW_MANY_PER_SECOND = 7733;
  private static final int HOW_MANY_TO_LOAD = 200000;
  private static final int REST_AFTER_THIS_MANY = 40;
  private static final int REST_FOR_MILLISECONDS = 5;

  private PositionSender() {}

  public static void main(String[] args) throws Exception {
    List<Holding> holdings = loadPositionsFromDatabase();

    System.out.println("loaded " + holdings.size() + " positions");
    System.out.println("sending about " + HOW_MANY_PER_SECOND + " a second to " + KAFKA_TOPIC);

    sendPositionsForever(holdings);
  }

  private static List<Holding> loadPositionsFromDatabase() throws SQLException {
    String askForThePositions =
        "SELECT account_id, product_id, how_many FROM position LIMIT " + HOW_MANY_TO_LOAD;
    List<Holding> holdings = new ArrayList<>();

    try (Connection database = Database.connect();
        Statement question = database.createStatement();
        ResultSet answer = question.executeQuery(askForThePositions)) {

      while (answer.next()) {
        int account = answer.getInt(1);
        int product = answer.getInt(2);
        double howMany = answer.getDouble(3);

        holdings.add(new Holding(account, product, howMany));
      }
    }

    if (holdings.isEmpty()) {
      throw new IllegalStateException("no positions found - run db/3-generate.sql first");
    }
    return holdings;
  }

  private static void sendPositionsForever(List<Holding> holdings) throws InterruptedException {
    Random dice = new Random();
    long howManySent = 0;

    try (KafkaProducer<String, String> kafka = Kafka.connect()) {
      while (true) {
        Holding holding = holdings.get(dice.nextInt(holdings.size()));
        holding.moveALittle(dice);

        Kafka.send(kafka, KAFKA_TOPIC, holding.messageKey(), holding.asMessage());

        howManySent = howManySent + 1;
        if (howManySent % 20000 == 0) {
          System.out.println("sent " + howManySent + " position updates");
        }

        boolean timeForAShortRest = howManySent % REST_AFTER_THIS_MANY == 0;
        if (timeForAShortRest) {
          Thread.sleep(REST_FOR_MILLISECONDS);
        }
      }
    }
  }

  private static final class Holding {

    private final int accountNumber;
    private final int productNumber;
    private final double startedAt;

    private double rightNow;

    Holding(int accountNumber, int productNumber, double startedAt) {
      this.accountNumber = accountNumber;
      this.productNumber = productNumber;
      this.startedAt = startedAt;
      this.rightNow = startedAt;
    }

    void moveALittle(Random dice) {
      double smallMove = (dice.nextDouble() - 0.5) * 0.02 * startedAt;

      rightNow = startedAt + smallMove;
    }

    String messageKey() {
      return Integer.toString(accountNumber);
    }

    String asMessage() {
      double rounded = Math.round(rightNow * 10000) / 10000.0;

      return String.format(
          "{\"accountId\":%d,\"productId\":%d,\"howMany\":%s}",
          accountNumber, productNumber, rounded);
    }
  }
}
