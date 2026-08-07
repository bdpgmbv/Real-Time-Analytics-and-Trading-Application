package vyshaliprabananthlal.stream.plumbing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaceTest {

  @Test
  @DisplayName("a slow rate is held to within a tenth of what was asked for")
  void aSlowRateIsHeld() throws InterruptedException {
    double measured = measureRate(100, 100);

    assertThat(measured).isBetween(90.0, 110.0);
  }

  @Test
  @DisplayName("a rate above one thousand a second is held, which the old sleep could not do")
  void aFastRateIsHeld() throws InterruptedException {
    double measured = measureRate(5000, 5000);

    assertThat(measured).isBetween(4500.0, 5500.0);
  }

  @Test
  @DisplayName("four thousand a second does not run away to two hundred thousand")
  void theBusyPriceRateIsHeld() throws InterruptedException {
    double measured = measureRate(4167, 4167);

    assertThat(measured).isLessThan(6000.0);
  }

  @Test
  @DisplayName("a rate of one a second waits a whole second between turns")
  void theSlowestRateWaits() throws InterruptedException {
    long startedAt = System.nanoTime();

    Pace pace = new Pace();
    pace.waitYourTurn(1);

    long tookMilliseconds = (System.nanoTime() - startedAt) / 1_000_000L;

    assertThat(tookMilliseconds).isBetween(900L, 1100L);
  }

  @Test
  @DisplayName("falling a long way behind does not build up a debt to be sprinted off later")
  void fallingBehindDoesNotBuildUpADebt() throws InterruptedException {
    Pace pace = new Pace();
    pace.waitYourTurn(1000);

    Thread.sleep(1500);

    long startedAt = System.nanoTime();
    for (int turn = 0; turn < 500; turn++) {
      pace.waitYourTurn(1000);
    }
    long tookMilliseconds = (System.nanoTime() - startedAt) / 1_000_000L;

    assertThat(tookMilliseconds).isGreaterThan(400L);
  }

  @Test
  @DisplayName("asking for no rate at all is refused instead of dividing by zero")
  void aRateOfZeroIsRefused() {
    Pace pace = new Pace();

    assertThatThrownBy(() -> pace.waitYourTurn(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least 1");
  }

  private double measureRate(int howManyPerSecond, int howManyTurns) throws InterruptedException {
    Pace pace = new Pace();
    long startedAt = System.nanoTime();

    for (int turn = 0; turn < howManyTurns; turn++) {
      pace.waitYourTurn(howManyPerSecond);
    }

    double secondsTaken = (System.nanoTime() - startedAt) / 1_000_000_000.0;
    return howManyTurns / secondsTaken;
  }
}
