package vyshaliprabananthlal.api.live;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * One boolean saying whether anything in the market has moved since we last looked.
 *
 * <p>A flag rather than a queue, on purpose. Prices arrive at two hundred a second and burst to
 * four thousand; forty-four thousand messages setting the same boolean cost nothing and leave
 * nothing to drain. A queue here would fall behind and would tell us nothing extra, because the
 * screen only ever wants the current answer.
 */
@Component
public class MarketChangeFlag {

    private final AtomicBoolean somethingMoved = new AtomicBoolean(false);

    @KafkaListener(
            topics = {"rtat.price", "rtat.fx-rate", "rtat.position", "rtat.trade"},
            groupId = "live-screens")
    public void onMessages(List<String> messages, Acknowledgment kafka) {
        if (!messages.isEmpty()) {
            somethingMoved.set(true);
        }
        kafka.acknowledge();
    }

    /** True once per batch of movement. Reading it clears it. */
    public boolean hasChanged() {
        return somethingMoved.getAndSet(false);
    }

    void markChanged() {
        somethingMoved.set(true);
    }
}
