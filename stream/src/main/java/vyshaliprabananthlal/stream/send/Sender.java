package vyshaliprabananthlal.stream.send;

public interface Sender {

  String name();

  void sendUntilStopped() throws InterruptedException;
}
