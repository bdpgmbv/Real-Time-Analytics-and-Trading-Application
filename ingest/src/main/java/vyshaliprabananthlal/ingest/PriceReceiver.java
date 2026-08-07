package vyshaliprabananthlal.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

public final class PriceReceiver {

  private static final String KAFKA_TOPIC = "rtat.price";
  private static final String GROUP_NAME = "price-receiver";
  private static final int HOW_MANY_AT_A_TIME = 2000;

  private static final String UPDATE_THE_PRICE =
      "UPDATE price SET price = ?, arrived_at = now() WHERE product_id = ?";

  private static final ObjectMapper JSON = new ObjectMapper();

  private PriceReceiver() {}

  public static void main(String[] args) throws Exception {
    System.out.println("reading " + KAFKA_TOPIC + " into " + Database.address());

    long howManyWritten = 0;

    try (KafkaConsumer<String, String> kafka = Consumer.connect(GROUP_NAME, HOW_MANY_AT_A_TIME);
        Connection database = Database.connect()) {

      database.setAutoCommit(false);
      kafka.subscribe(List.of(KAFKA_TOPIC));

      while (true) {
        ConsumerRecords<String, String> batch = kafka.poll(Duration.ofMillis(500));
        if (batch.isEmpty()) {
          continue;
        }

        howManyWritten = howManyWritten + writeToDatabase(batch, database);
        kafka.commitSync();

        System.out.println("rows actually changed: " + howManyWritten + "   price changes");
      }
    }
  }

  private static int writeToDatabase(ConsumerRecords<String, String> batch, Connection database)
      throws SQLException {

    int howManyInThisBatch = 0;

    try (PreparedStatement update = database.prepareStatement(UPDATE_THE_PRICE)) {
      for (ConsumerRecord<String, String> message : batch) {
        JsonNode fields = readJson(message.value());

        update.setDouble(1, fields.path("price").asDouble());
        update.setInt(2, fields.path("productId").asInt());
        update.addBatch();
      }

      int[] howManyRowsEachChanged = update.executeBatch();
      for (int rowsChanged : howManyRowsEachChanged) {
        howManyInThisBatch = howManyInThisBatch + Math.max(0, rowsChanged);
      }
    }

    database.commit();
    return howManyInThisBatch;
  }

  private static JsonNode readJson(String message) {
    try {
      return JSON.readTree(message);
    } catch (Exception problem) {
      throw new IllegalStateException("could not read message: " + message, problem);
    }
  }
}
