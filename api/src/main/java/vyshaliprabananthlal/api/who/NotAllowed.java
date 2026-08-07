package vyshaliprabananthlal.api.who;

public class NotAllowed extends RuntimeException {

  public NotAllowed(String whatWasRefused) {
    super(whatWasRefused);
  }
}
