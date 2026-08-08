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

/**
 * Who currently has a screen open, and on which fund.
 *
 * <p>The cost of pushing updates follows the number of open screens, not the amount the
 * market moves. Nobody watching means no work at all.
 *
 * <p>A browser that closes its tab is dropped on the next send rather than announced, so
 * the registry cleans itself up without anyone having to tell it.
 */
@Component
public class ScreenRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(ScreenRegistry.class);

    private final Map<Integer, List<SseEmitter>> watchers = new ConcurrentHashMap<>();

    public void add(int fundId, SseEmitter screen) {
        watchers.computeIfAbsent(fundId, ignored -> new CopyOnWriteArrayList<>())
                .add(screen);

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

    public void sendTo(int fundId, String eventName, Object payload) {
        for (SseEmitter screen : watchers.getOrDefault(fundId, List.of())) {
            try {
                screen.send(SseEmitter.event().name(eventName).data(payload));
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
