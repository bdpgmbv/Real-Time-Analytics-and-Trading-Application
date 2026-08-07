package vyshaliprabananthlal.ingest.format;

public class BadLine extends RuntimeException {

  private final String whatIsWrong;

  public BadLine(String whatIsWrong) {
    super(whatIsWrong);
    this.whatIsWrong = whatIsWrong;
  }

  public String whatIsWrong() {
    return whatIsWrong;
  }
}
