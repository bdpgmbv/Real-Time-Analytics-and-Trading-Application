package vyshaliprabananthlal.stream.send;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SenderSelector implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(SenderSelector.class);

  private final List<Sender> everySender;
  private final String wanted;

  public SenderSelector(List<Sender> everySender, @Value("${rtat.send:}") String wanted) {
    this.everySender = everySender;
    this.wanted = wanted;
  }

  @Override
  public void run(ApplicationArguments arguments) throws InterruptedException {
    if (wanted.isBlank()) {
      LOG.error("say which one to run, for example --rtat.send=price");
      LOG.error("the ones we have: {}", knownNames());
      return;
    }

    Sender chosen = findIt(wanted);
    LOG.info("starting the {} sender", chosen.name());

    chosen.sendContinuously();
  }

  private Sender findIt(String name) {
    for (Sender sender : everySender) {
      if (sender.name().equalsIgnoreCase(name)) {
        return sender;
      }
    }
    throw new IllegalArgumentException("no sender called " + name + ", we have: " + knownNames());
  }

  private List<String> knownNames() {
    return everySender.stream().map(Sender::name).sorted().toList();
  }
}
