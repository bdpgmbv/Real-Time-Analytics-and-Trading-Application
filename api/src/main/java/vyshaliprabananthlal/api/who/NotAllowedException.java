package vyshaliprabananthlal.api.who;

public class NotAllowedException extends RuntimeException {

  public NotAllowedException(String whatWasRefused) {
    super(whatWasRefused);
  }
}
