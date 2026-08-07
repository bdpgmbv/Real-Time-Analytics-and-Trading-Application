package vyshaliprabananthlal.stream.plumbing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class SendToKafkaTest {

  private KafkaTemplate<String, String> template;
  private SimpleMeterRegistry meters;
  private SendToKafka sending;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    template = mock(KafkaTemplate.class);
    meters = new SimpleMeterRegistry();
    sending = new SendToKafka(template, meters);
  }

  @Test
  @DisplayName("a message that lands is counted against its topic")
  void aMessageThatLandsIsCounted() {
    whenSendingItLands();

    sending.send("rtat.price", "1", "{}");

    assertThat(sentCount("rtat.price")).isEqualTo(1.0);
  }

  @Test
  @DisplayName("a message that fails is counted separately, which is what the alert watches")
  void aMessageThatFailsIsCountedSeparately() {
    whenSendingItFails();

    sending.send("rtat.price", "1", "{}");

    assertThat(failedCount("rtat.price")).isEqualTo(1.0);
    assertThat(meters.find("rtat.messages.sent").counter()).isNull();
  }

  @Test
  @DisplayName("each topic is counted on its own, so one bad feed is visible")
  void eachTopicIsCountedOnItsOwn() {
    whenSendingItLands();

    sending.send("rtat.price", "1", "{}");
    sending.send("rtat.price", "2", "{}");
    sending.send("rtat.trade", "1", "{}");

    assertThat(sentCount("rtat.price")).isEqualTo(2.0);
    assertThat(sentCount("rtat.trade")).isEqualTo(1.0);
  }

  @Test
  @DisplayName("the key is handed to Kafka, because it decides the partition")
  void theKeyIsHandedToKafka() {
    whenSendingItLands();

    sending.send("rtat.position", "340", "{}");

    verify(template).send("rtat.position", "340", "{}");
  }

  @SuppressWarnings("unchecked")
  private void whenSendingItLands() {
    SendResult<String, String> landed = mock(SendResult.class);

    when(template.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(landed));
  }

  private void whenSendingItFails() {
    when(template.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker is gone")));
  }

  private double sentCount(String topic) {
    return meters.get("rtat.messages.sent").tag("topic", topic).counter().count();
  }

  private double failedCount(String topic) {
    return meters.get("rtat.messages.failed").tag("topic", topic).counter().count();
  }
}
