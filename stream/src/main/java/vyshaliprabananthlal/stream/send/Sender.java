package vyshaliprabananthlal.stream.send;

public interface Sender {

  String name();

  void sendContinuously() throws InterruptedException;
}
