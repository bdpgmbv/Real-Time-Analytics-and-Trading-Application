package vyshaliprabananthlal.api.live;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class ScreenRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(ScreenRegistry.class);

    private final Map<Integer, List<SseEmitter>> watchers = new ConcurrentHashMap<>();

    public void add(int fundId, SseEmitter screen) {
        watchers.computeIfAbsent(fundId, which -> new CopyOnWriteArrayList<>()).add(screen);

        screen.onCompletion(() -> remove(fundId, screen));
        screen.onTimeout(() -> remove(fundId, screen));
        screen.onError(whatWentWrong -> remove(fundId, screen));

        LOG.info("a screen opened on fund {}, now {} watching", fundId, watcherCount(fundId));
    }

    public Set<Integer> watchedFunds() {
        return watchers.keySet();
    }

    public int watcherCount(int fundId) {
        return watchers.getOrDefault(fundId, List.of()).size();
    }

    public void sendTo(int fundId, String eventName, Object what) {
        for (SseEmitter screen : watchers.getOrDefault(fundId, List.of())) {
            try {
                screen.send(SseEmitter.event().name(eventName).data(what));
            } catch (IOException theScreenWentAway) {
                remove(fundId, screen);
            } catch (IllegalStateException alreadyFinished) {
                remove(fundId, screen);
            }
        }
    }

    private void remove(int fundId, SseEmitter screen) {
        List<SseEmitter> onThisFund = watchers.get(fundId);
        if (onThisFund == null) {
            return;
        }

        onThisFund.remove(screen);
        if (onThisFund.isEmpty()) {
            watchers.remove(fundId);
        }
    }
}
