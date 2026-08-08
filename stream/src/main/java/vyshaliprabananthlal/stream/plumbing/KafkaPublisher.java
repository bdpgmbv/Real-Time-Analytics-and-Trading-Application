package vyshaliprabananthlal.stream.plumbing;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaPublisher {

  private static final Logger LOG = LoggerFactory.getLogger(KafkaPublisher.class);

  private final KafkaTemplate<String, String> kafka;
  private final MeterRegistry meters;
  private final Map<String, Counter> sentCounters = new ConcurrentHashMap<>();
  private final Map<String, Counter> failedCounters = new ConcurrentHashMap<>();

  public KafkaPublisher(KafkaTemplate<String, String> kafka, MeterRegistry meters) {
    this.kafka = kafka;
    this.meters = meters;
  }

  public void send(String topic, String key, String message) {
    var unused =
        kafka
            .send(topic, key, message)
            .whenComplete(
                (whereItLanded, problem) -> {
                  if (problem == null) {
                    counter(sentCounters, "rtat.messages.sent", topic).increment();
                  } else {
                    counter(failedCounters, "rtat.messages.failed", topic).increment();
                    LOG.error("could not send to {}: {}", topic, problem.getMessage());
                  }
                });
  }

  private Counter counter(Map<String, Counter> cache, String name, String topic) {
    return cache.computeIfAbsent(
        topic, whichTopic -> Counter.builder(name).tag("topic", whichTopic).register(meters));
  }
}
