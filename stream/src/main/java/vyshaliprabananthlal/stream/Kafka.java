package vyshaliprabananthlal.stream;

import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

public final class Kafka {

  private static final String DEFAULT_ADDRESS = "localhost:9092";
  private static final String TEXT = "org.apache.kafka.common.serialization.StringSerializer";

  private Kafka() {}

  public static String address() {
    return System.getenv().getOrDefault("RTAT_KAFKA", DEFAULT_ADDRESS);
  }

  public static KafkaProducer<String, String> connect() {
    Properties settings = new Properties();

    settings.put("bootstrap.servers", address());
    settings.put("key.serializer", TEXT);
    settings.put("value.serializer", TEXT);
    settings.put("acks", "all");
    settings.put("linger.ms", "20");

    return new KafkaProducer<>(settings);
  }

  public static void send(
      KafkaProducer<String, String> kafka, String topic, String key, String message) {

    ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, message);

    var unused =
        kafka.send(
            record,
            (whereItLanded, problem) -> {
              if (problem != null) {
                System.out.println("could not send: " + problem.getMessage());
              }
            });
  }
}
