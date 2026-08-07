package vyshaliprabananthlal.stream;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.apache.kafka.clients.producer.KafkaProducer;

public final class TradeSender {

  private static final String KAFKA_TOPIC = "rtat.trade";
  private static final int HOW_MANY_PER_SECOND = 8;
  private static final int HOW_MANY_TO_LOAD = 100000;

  private TradeSender() {}

  public static void main(String[] args) throws Exception {
    List<WhatToTrade> choices = loadAccountsAndProducts();

    System.out.println("loaded " + choices.size() + " account and product pairs");
    System.out.println("sending " + HOW_MANY_PER_SECOND + " trades a second to " + KAFKA_TOPIC);

    sendTradesForever(choices);
  }

  private static List<WhatToTrade> loadAccountsAndProducts() throws SQLException {
    String askForThePairs = "SELECT account_id, product_id FROM position LIMIT " + HOW_MANY_TO_LOAD;
    List<WhatToTrade> choices = new ArrayList<>();

    try (Connection database = Database.connect();
        Statement question = database.createStatement();
        ResultSet answer = question.executeQuery(askForThePairs)) {

      while (answer.next()) {
        int account = answer.getInt(1);
        int product = answer.getInt(2);

        choices.add(new WhatToTrade(account, product));
      }
    }

    if (choices.isEmpty()) {
      throw new IllegalStateException("no positions found - run db/3-generate.sql first");
    }
    return choices;
  }

  private static void sendTradesForever(List<WhatToTrade> choices) throws InterruptedException {
    Random dice = new Random();
    long nextTradeNumber = 1;
    long howManySent = 0;

    try (KafkaProducer<String, String> kafka = Kafka.connect()) {
      while (true) {
        WhatToTrade choice = choices.get(dice.nextInt(choices.size()));
        String message = choice.newTrade(nextTradeNumber, dice);

        Kafka.send(kafka, KAFKA_TOPIC, choice.messageKey(), message);

        nextTradeNumber = nextTradeNumber + 1;
        howManySent = howManySent + 1;
        if (howManySent % 20 == 0) {
          System.out.println("sent " + howManySent + " trades");
        }

        Thread.sleep(1000 / HOW_MANY_PER_SECOND);
      }
    }
  }

  private static final class WhatToTrade {

    private final int accountNumber;
    private final int productNumber;

    WhatToTrade(int accountNumber, int productNumber) {
      this.accountNumber = accountNumber;
      this.productNumber = productNumber;
    }

    String messageKey() {
      return Integer.toString(accountNumber);
    }

    String newTrade(long tradeNumber, Random dice) {
      long howMany = 1L + dice.nextInt(10000);
      boolean itIsASale = dice.nextInt(2) == 0;
      if (itIsASale) {
        howMany = -howMany;
      }

      double price = Math.round((5 + dice.nextDouble() * 300) * 100) / 100.0;
      boolean typedInByHand = dice.nextInt(10) == 0;
      String cameFrom = typedInByHand ? "SOMEONE UPLOADED IT" : "AUTOMATIC FEED";

      return String.format(
          "{\"tradeId\":%d,\"accountId\":%d,\"productId\":%d,\"howMany\":%d,"
              + "\"price\":%s,\"happenedAt\":\"%s\",\"cameFrom\":\"%s\"}",
          tradeNumber, accountNumber, productNumber, howMany, price, Instant.now(), cameFrom);
    }
  }
}
