package vyshaliprabananthlal.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MessagesTest {

  private final Messages messages = new Messages();

  @Test
  @DisplayName("reads the fields out of a rate message")
  void readsARateMessage() {
    JsonNode fields = messages.read("{\"from\":\"JPY\",\"to\":\"USD\",\"rate\":0.006336}");

    assertThat(fields.path("from").asText()).isEqualTo("JPY");
    assertThat(fields.path("to").asText()).isEqualTo("USD");
    assertThat(fields.path("rate").asDouble()).isEqualTo(0.006336);
  }

  @Test
  @DisplayName("a message that is not JSON fails loudly, showing the message")
  void badMessageFailsLoudly() {
    assertThatThrownBy(() -> messages.read("this is not json"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("this is not json");
  }

  @Test
  @DisplayName("a missing field reads as empty rather than throwing")
  void missingFieldIsEmpty() {
    JsonNode fields = messages.read("{\"from\":\"JPY\"}");

    assertThat(fields.path("to").asText()).isEmpty();
    assertThat(fields.path("rate").asDouble()).isZero();
  }

  @Test
  @DisplayName("counts the rows the database says it changed")
  void countsRowsChanged() {
    assertThat(messages.howManyRowsChanged(new int[] {1, 1, 1})).isEqualTo(3);
    assertThat(messages.howManyRowsChanged(new int[] {1, 0, 1})).isEqualTo(2);
    assertThat(messages.howManyRowsChanged(new int[] {})).isZero();
  }

  @Test
  @DisplayName("a batch result of SUCCESS_NO_INFO is not counted as negative")
  void noInfoIsNotNegative() {
    assertThat(messages.howManyRowsChanged(new int[] {-2, -2})).isZero();
    assertThat(messages.howManyRowsChanged(new int[] {-3, 5})).isEqualTo(5);
  }
}
