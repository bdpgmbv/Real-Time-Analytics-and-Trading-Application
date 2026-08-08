package vyshaliprabananthlal.api.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;
import vyshaliprabananthlal.calculate.exposure.Exposure;
import vyshaliprabananthlal.calculate.exposure.ExposureCalculator;
import vyshaliprabananthlal.calculate.exposure.FundExposure;

class PushToTheScreensTest {

  private ScreenRegistry watching;
  private MarketChangeFlag changes;
  private ExposureCalculator calculator;
  private SimpleMeterRegistry meters;
  private ExposurePublisher pushing;

  @BeforeEach
  void setUp() {
    watching = mock(ScreenRegistry.class);
    changes = new MarketChangeFlag();
    calculator = mock(ExposureCalculator.class);
    meters = new SimpleMeterRegistry();
    pushing = new ExposurePublisher(watching, changes, calculator, meters);
  }

  @Test
  @DisplayName("with nobody watching, nothing is calculated however much moves")
  void nobodyWatchingMeansNoWork() {
    when(watching.fundsBeingWatched()).thenReturn(Set.of());
    changes.pretendSomethingMoved();

    pushing.pushWhateverMoved();

    verify(calculator, never()).forWholeFund(anyInt());
  }

  @Test
  @DisplayName("with nothing moving, a watched fund is not recalculated")
  void nothingMovingMeansNoWork() {
    when(watching.fundsBeingWatched()).thenReturn(Set.of(1));

    pushing.pushWhateverMoved();

    verify(calculator, never()).forWholeFund(anyInt());
  }

  @Test
  @DisplayName("a whole burst of messages causes one push, not one push per message")
  void aBurstCausesOnePush() {
    when(watching.fundsBeingWatched()).thenReturn(Set.of(1));
    when(calculator.forWholeFund(1)).thenReturn(exposureOf("EUR", 5000000));

    Acknowledgment kafka = mock(Acknowledgment.class);
    for (int message = 0; message < 4167; message++) {
      changes.whenAnythingArrives(List.of("{}"), kafka);
    }

    pushing.pushWhateverMoved();

    verify(calculator, times(1)).forWholeFund(1);
    verify(watching, times(1)).sendTo(eq(1), anyString(), any());
  }

  @Test
  @DisplayName("a second sweep with nothing new does nothing at all")
  void aSecondSweepWithNothingNewDoesNothing() {
    when(watching.fundsBeingWatched()).thenReturn(Set.of(1));
    when(calculator.forWholeFund(1)).thenReturn(exposureOf("EUR", 5000000));
    changes.pretendSomethingMoved();

    pushing.pushWhateverMoved();
    pushing.pushWhateverMoved();

    verify(calculator, times(1)).forWholeFund(1);
  }

  @Test
  @DisplayName("numbers that have not actually changed are not sent to the screen again")
  void unchangedNumbersAreNotSentTwice() {
    when(watching.fundsBeingWatched()).thenReturn(Set.of(1));
    when(calculator.forWholeFund(1)).thenReturn(exposureOf("EUR", 5000000));

    changes.pretendSomethingMoved();
    pushing.pushWhateverMoved();

    changes.pretendSomethingMoved();
    pushing.pushWhateverMoved();

    verify(calculator, times(2)).forWholeFund(1);
    verify(watching, times(1)).sendTo(eq(1), anyString(), any());
    assertThat(meters.get("rtat.live.unchanged").counter().count()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("numbers that did change are sent")
  void changedNumbersAreSent() {
    when(watching.fundsBeingWatched()).thenReturn(Set.of(1));
    when(calculator.forWholeFund(1))
        .thenReturn(exposureOf("EUR", 5000000))
        .thenReturn(exposureOf("EUR", 5100000));

    changes.pretendSomethingMoved();
    pushing.pushWhateverMoved();
    changes.pretendSomethingMoved();
    pushing.pushWhateverMoved();

    verify(watching, times(2)).sendTo(eq(1), anyString(), any());
    assertThat(meters.get("rtat.live.pushed").counter().count()).isEqualTo(2.0);
  }

  @Test
  @DisplayName("a rounding-level wobble is not treated as a change worth sending")
  void aTinyWobbleIsNotAChange() {
    when(watching.fundsBeingWatched()).thenReturn(Set.of(1));
    when(calculator.forWholeFund(1))
        .thenReturn(exposureOf("EUR", 5000000.4))
        .thenReturn(exposureOf("EUR", 5000000.3));

    changes.pretendSomethingMoved();
    pushing.pushWhateverMoved();
    changes.pretendSomethingMoved();
    pushing.pushWhateverMoved();

    verify(watching, times(1)).sendTo(eq(1), anyString(), any());
  }

  @Test
  @DisplayName("a calculation that fails does not stop the other funds being pushed")
  void oneBadFundDoesNotStopTheRest() {
    when(watching.fundsBeingWatched()).thenReturn(new java.util.TreeSet<>(Set.of(1, 2)));
    when(calculator.forWholeFund(1)).thenThrow(new IllegalStateException("the database went away"));
    when(calculator.forWholeFund(2)).thenReturn(exposureOf("EUR", 1000000));
    changes.pretendSomethingMoved();

    pushing.pushWhateverMoved();

    verify(watching, times(1)).sendTo(eq(2), anyString(), any());
  }

  @Test
  @DisplayName("every watched fund is pushed, not only the first")
  void everyWatchedFundIsPushed() {
    when(watching.fundsBeingWatched()).thenReturn(new java.util.TreeSet<>(Set.of(1, 2, 3)));
    when(calculator.forWholeFund(anyInt())).thenReturn(exposureOf("EUR", 1000000));
    changes.pretendSomethingMoved();

    pushing.pushWhateverMoved();

    verify(calculator, times(3)).forWholeFund(anyInt());
  }

  @Test
  @DisplayName("an empty poll from Kafka is not treated as something having moved")
  void anEmptyPollIsNotAChange() {
    Acknowledgment kafka = mock(Acknowledgment.class);

    changes.whenAnythingArrives(List.of(), kafka);

    assertThat(changes.anythingMoved()).isFalse();
    verify(kafka).acknowledge();
  }

  private FundExposure exposureOf(String currency, double amount) {
    return new FundExposure(1, "USD", List.of(new Exposure(currency, amount, amount)), 2);
  }
}
