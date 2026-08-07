package vyshaliprabananthlal.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

public final class PositionReceiver {

  private static final String KAFKA_TOPIC = "rtat.position";
  private static final String GROUP_NAME = "position-receiver";

  private static final String UPDATE_THE_POSITION =
      "UPDATE position SET how_many = ? WHERE account_id = ? AND product_id = ?";

  private static final ObjectMapper JSON = new ObjectMapper();

  private PositionReceiver() {}

  public static void main(String[] args) throws Exception {
    System.out.println("reading " + KAFKA_TOPIC + " into " + Database.address());

    long howManyWritten = 0;

    try (KafkaConsumer<String, String> kafka = connectToKafka();
        Connection database = Database.connect()) {

      database.setAutoCommit(false);
      kafka.subscribe(List.of(KAFKA_TOPIC));

      while (true) {
        ConsumerRecords<String, String> batch = kafka.poll(Duration.ofMillis(500));
        if (batch.isEmpty()) {
          continue;
        }

        howManyWritten = howManyWritten + writeToDatabase(batch, database);

        crashHereIfWeAreTestingWhatHappens();

        kafka.commitSync();

        System.out.println("written " + howManyWritten + " position changes");
      }
    }
  }

  private static void crashHereIfWeAreTestingWhatHappens() {
    boolean weAreTesting = "true".equals(System.getenv("RTAT_CRASH_AFTER_COMMIT"));
    if (weAreTesting) {
      throw new IllegalStateException("pretending the process died right here");
    }
  }

  private static int writeToDatabase(ConsumerRecords<String, String> batch, Connection database)
      throws SQLException {

    int howManyInThisBatch = 0;

    try (PreparedStatement update = database.prepareStatement(UPDATE_THE_POSITION)) {
      for (ConsumerRecord<String, String> message : batch) {
        JsonNode fields = readJson(message.value());

        int account = fields.path("accountId").asInt();
        int product = fields.path("productId").asInt();
        double howMany = fields.path("howMany").asDouble();

        update.setDouble(1, howMany);
        update.setInt(2, account);
        update.setInt(3, product);
        update.addBatch();

        howManyInThisBatch = howManyInThisBatch + 1;
      }
      update.executeBatch();
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

  private static KafkaConsumer<String, String> connectToKafka() {
    String text = "org.apache.kafka.common.serialization.StringDeserializer";

    Properties settings = new Properties();
    settings.put("bootstrap.servers", System.getenv().getOrDefault("RTAT_KAFKA", "localhost:9092"));
    settings.put("group.id", GROUP_NAME);
    settings.put("key.deserializer", text);
    settings.put("value.deserializer", text);
    settings.put("auto.offset.reset", "earliest");
    settings.put("enable.auto.commit", "false");
    settings.put("max.poll.records", "1000");

    return new KafkaConsumer<>(settings);
  }
}
