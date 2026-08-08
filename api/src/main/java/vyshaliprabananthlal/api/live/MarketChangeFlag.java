package vyshaliprabananthlal.api.live;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class MarketChangeFlag {

  private final AtomicBoolean anythingMovedSinceWeLastLooked = new AtomicBoolean(false);

  @KafkaListener(
      topics = {"rtat.price", "rtat.fx-rate", "rtat.position", "rtat.trade"},
      groupId = "live-screens")
  public void whenAnythingArrives(java.util.List<String> arrived, Acknowledgment kafka) {
    if (!arrived.isEmpty()) {
      anythingMovedSinceWeLastLooked.set(true);
    }
    kafka.acknowledge();
  }

  public boolean hasChanged() {
    return anythingMovedSinceWeLastLooked.getAndSet(false);
  }

  void markChanged() {
    anythingMovedSinceWeLastLooked.set(true);
  }
}
