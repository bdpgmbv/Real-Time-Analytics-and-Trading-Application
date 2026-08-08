package vyshaliprabananthlal.ingest.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WireFormatTest {

    private final JsonReader reading = new JsonReader();

    @Test
    @DisplayName("a price message keeps the field names anyone integrating already uses")
    void priceFieldsAreFixed() {
        JsonNode fields = reading.read("{\"productId\":100101,\"price\":189.32,\"howFresh\":\"DELAYED 20 MINUTES\"}");

        assertThat(fields.path("productId").asInt()).isEqualTo(100101);
        assertThat(fields.path("price").asDouble()).isEqualTo(189.32);
    }

    @Test
    @DisplayName("a position message keeps its field names")
    void positionFieldsAreFixed() {
        JsonNode fields = reading.read("{\"accountId\":340,\"productId\":100102,\"howMany\":500.25}");

        assertThat(fields.path("accountId").asInt()).isEqualTo(340);
        assertThat(fields.path("productId").asInt()).isEqualTo(100102);
        assertThat(fields.path("howMany").asDouble()).isEqualTo(500.25);
    }

    @Test
    @DisplayName("a trade message keeps its field names")
    void tradeFieldsAreFixed() {
        JsonNode fields = reading.read("{\"tradeId\":77,\"accountId\":340,\"productId\":100102,\"howMany\":-500,"
                + "\"price\":42.5,\"happenedAt\":\"2026-08-06T14:32:11Z\","
                + "\"cameFrom\":\"AUTOMATIC FEED\"}");

        assertThat(fields.path("tradeId").asLong()).isEqualTo(77);
        assertThat(fields.path("howMany").asDouble()).isEqualTo(-500);
        assertThat(fields.path("cameFrom").asText()).isEqualTo("AUTOMATIC FEED");
    }

    @Test
    @DisplayName("an fx rate message keeps its field names")
    void rateFieldsAreFixed() {
        JsonNode fields = reading.read("{\"from\":\"EUR\",\"to\":\"USD\",\"rate\":1.1542}");

        assertThat(fields.path("from").asText()).isEqualTo("EUR");
        assertThat(fields.path("to").asText()).isEqualTo("USD");
        assertThat(fields.path("rate").asDouble()).isEqualTo(1.1542);
    }

    @Test
    @DisplayName("a hedge fill message keeps its field names")
    void hedgeFillFieldsAreFixed() {
        JsonNode fields = reading.read("{\"fillId\":1,\"hedgeId\":500,\"amountFilled\":9000000,\"rate\":1.154,"
                + "\"filledAt\":\"2026-08-06T14:32:11Z\",\"theirReference\":\"FXM-77120-1\"}");

        assertThat(fields.path("fillId").asLong()).isEqualTo(1);
        assertThat(fields.path("amountFilled").asDouble()).isEqualTo(9000000);
        assertThat(fields.path("theirReference").asText()).isEqualTo("FXM-77120-1");
    }

    @Test
    @DisplayName("the wire says howMany even though the column says quantity, and that is on purpose")
    void theWireIsNotTheSchema() {
        JsonNode fields = reading.read("{\"accountId\":1,\"productId\":1,\"howMany\":10}");

        assertThat(fields.path("howMany").asDouble()).isEqualTo(10);
        assertThat(fields.path("quantity").isMissingNode()).isTrue();
    }

    @Test
    @DisplayName("a field we do not know is ignored, so adding one never breaks an older reader")
    void unknownFieldsAreIgnored() {
        JsonNode fields = reading.read("{\"productId\":1,\"price\":2.5,\"somethingAddedLater\":\"whatever\"}");

        assertThat(fields.path("price").asDouble()).isEqualTo(2.5);
    }
}
