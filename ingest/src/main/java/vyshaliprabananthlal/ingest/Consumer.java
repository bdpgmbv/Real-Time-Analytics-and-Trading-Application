package vyshaliprabananthlal.ingest;

import java.util.Properties;
import org.apache.kafka.clients.consumer.KafkaConsumer;

public final class Consumer {

  private static final String DEFAULT_ADDRESS = "localhost:9092";
  private static final String TEXT = "org.apache.kafka.common.serialization.StringDeserializer";

  private Consumer() {}

  public static KafkaConsumer<String, String> connect(String groupName, int howManyAtATime) {
    Properties settings = new Properties();

    settings.put("bootstrap.servers", System.getenv().getOrDefault("RTAT_KAFKA", DEFAULT_ADDRESS));
    settings.put("group.id", groupName);
    settings.put("key.deserializer", TEXT);
    settings.put("value.deserializer", TEXT);
    settings.put("auto.offset.reset", "earliest");
    settings.put("enable.auto.commit", "false");
    settings.put("max.poll.records", Integer.toString(howManyAtATime));

    return new KafkaConsumer<>(settings);
  }
}
