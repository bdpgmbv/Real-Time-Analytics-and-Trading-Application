package vyshaliprabananthlal.stream.plumbing;

public final class SendRate {

  private static final long ONE_SECOND = 1_000_000_000L;
  private static final long ONE_MILLISECOND = 1_000_000L;

  private long sendTheNextOneAt;

  public SendRate() {
    this.sendTheNextOneAt = System.nanoTime();
  }

  public void waitYourTurn(int howManyPerSecond) throws InterruptedException {
    if (howManyPerSecond < 1) {
      throw new IllegalArgumentException("how many per second must be at least 1");
    }

    sendTheNextOneAt = sendTheNextOneAt + ONE_SECOND / howManyPerSecond;

    long now = System.nanoTime();
    long weAreThisFarBehind = now - sendTheNextOneAt;

    if (weAreThisFarBehind > ONE_SECOND) {
      sendTheNextOneAt = now;
      return;
    }

    long howLongToWait = sendTheNextOneAt - now;
    if (howLongToWait > ONE_MILLISECOND) {
      Thread.sleep(howLongToWait / ONE_MILLISECOND);
    }
  }
}
