package vyshaliprabananthlal.ingest.format;

public class BadLineException extends RuntimeException {

  private final String whatIsWrong;

  public BadLineException(String whatIsWrong) {
    super(whatIsWrong);
    this.whatIsWrong = whatIsWrong;
  }

  public String whatIsWrong() {
    return whatIsWrong;
  }
}
