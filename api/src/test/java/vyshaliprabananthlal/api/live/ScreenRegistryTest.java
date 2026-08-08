package vyshaliprabananthlal.api.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class WhoIsWatchingTest {

  private final ScreenRegistry watching = new ScreenRegistry();

  @Test
  @DisplayName("a screen that opens is counted against its fund")
  void aScreenIsCounted() {
    watching.add(1, new SseEmitter());

    assertThat(watching.howMany(1)).isEqualTo(1);
    assertThat(watching.fundsBeingWatched()).containsExactly(1);
  }

  @Test
  @DisplayName("two screens on the same fund are both counted")
  void twoScreensOnOneFund() {
    watching.add(1, new SseEmitter());
    watching.add(1, new SseEmitter());

    assertThat(watching.howMany(1)).isEqualTo(2);
    assertThat(watching.fundsBeingWatched()).containsExactly(1);
  }

  @Test
  @DisplayName("screens on different funds are kept apart")
  void differentFundsAreKeptApart() {
    watching.add(1, new SseEmitter());
    watching.add(2, new SseEmitter());

    assertThat(watching.fundsBeingWatched()).containsExactlyInAnyOrder(1, 2);
  }

  @Test
  @DisplayName("a fund nobody is watching reports nobody")
  void anUnwatchedFundReportsNobody() {
    assertThat(watching.howMany(99)).isZero();
    assertThat(watching.fundsBeingWatched()).isEmpty();
  }

  @Test
  @DisplayName("sending to a fund nobody watches does nothing rather than failing")
  void sendingToNobodyIsFine() {
    watching.sendTo(99, "exposure", "anything");

    assertThat(watching.fundsBeingWatched()).isEmpty();
  }

  @Test
  @DisplayName("what is sent reaches every screen on that fund")
  void everyScreenGetsIt() throws IOException {
    SseEmitter one = mock(SseEmitter.class);
    SseEmitter two = mock(SseEmitter.class);
    watching.add(1, one);
    watching.add(1, two);

    watching.sendTo(1, "exposure", "the numbers");

    verify(one).send(any(SseEmitter.SseEventBuilder.class));
    verify(two).send(any(SseEmitter.SseEventBuilder.class));
  }

  @Test
  @DisplayName("a browser that closed its tab is dropped, and the rest still get theirs")
  void aClosedBrowserIsDropped() throws IOException {
    SseEmitter goneAway = mock(SseEmitter.class);
    SseEmitter stillThere = mock(SseEmitter.class);
    doThrow(new IOException("broken pipe"))
        .when(goneAway)
        .send(any(SseEmitter.SseEventBuilder.class));

    watching.add(1, goneAway);
    watching.add(1, stillThere);

    watching.sendTo(1, "exposure", "the numbers");

    assertThat(watching.howMany(1)).isEqualTo(1);
    verify(stillThere).send(any(SseEmitter.SseEventBuilder.class));
  }

  @Test
  @DisplayName("a screen that already finished is dropped rather than throwing on every push")
  void anAlreadyFinishedScreenIsDropped() throws IOException {
    SseEmitter finished = mock(SseEmitter.class);
    doThrow(new IllegalStateException("already completed"))
        .when(finished)
        .send(any(SseEmitter.SseEventBuilder.class));

    watching.add(1, finished);
    watching.sendTo(1, "exposure", "the numbers");

    assertThat(watching.howMany(1)).isZero();
  }

  @Test
  @DisplayName("when the last screen on a fund goes, the fund stops being watched at all")
  void theLastScreenLeavingStopsTheWork() throws IOException {
    SseEmitter onlyOne = mock(SseEmitter.class);
    doThrow(new IOException("gone")).when(onlyOne).send(any(SseEmitter.SseEventBuilder.class));

    watching.add(1, onlyOne);
    watching.sendTo(1, "exposure", "the numbers");

    assertThat(watching.fundsBeingWatched()).isEmpty();
  }
}
