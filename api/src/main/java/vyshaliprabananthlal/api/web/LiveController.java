package vyshaliprabananthlal.api.web;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vyshaliprabananthlal.api.live.ExposurePublisher;
import vyshaliprabananthlal.api.live.ScreenRegistry;
import vyshaliprabananthlal.api.security.CallerIdentity;
import vyshaliprabananthlal.api.who.Entitlements;

@RestController
@RequestMapping("/api/funds/{fundId}/exposure")
public class LiveController {

  private final Entitlements entitlements;
  private final ScreenRegistry watching;
  private final ExposurePublisher pushing;
  private final CallerIdentity whoIsAsking;
  private final Duration howLongAScreenMayStayOpen;

  public LiveController(
      Entitlements entitlements,
      ScreenRegistry watching,
      ExposurePublisher pushing,
      CallerIdentity whoIsAsking,
      @Value("${rtat.live.screen-timeout-minutes:30}") long timeoutMinutes) {

    this.entitlements = entitlements;
    this.watching = watching;
    this.pushing = pushing;
    this.whoIsAsking = whoIsAsking;
    this.howLongAScreenMayStayOpen = Duration.ofMinutes(timeoutMinutes);
  }

  @GetMapping(value = "/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter watchThisFund(@PathVariable int fundId, Authentication token) {
    entitlements.mustBeAbleToSee(whoIsAsking.userId(token), fundId);

    SseEmitter screen = new SseEmitter(howLongAScreenMayStayOpen.toMillis());
    watching.add(fundId, screen);

    pushing.pushOneFund(fundId);

    return screen;
  }
}
