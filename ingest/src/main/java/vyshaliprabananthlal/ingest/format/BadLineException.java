package vyshaliprabananthlal.ingest.format;

public class BadLineException extends RuntimeException {

  private final String reason;

  public BadLineException(String reason) {
    super(reason);
    this.reason = reason;
  }

  public String reason() {
    return reason;
  }
}
