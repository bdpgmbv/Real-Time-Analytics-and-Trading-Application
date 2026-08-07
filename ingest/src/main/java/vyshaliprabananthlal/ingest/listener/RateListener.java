package vyshaliprabananthlal.ingest.listener;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import vyshaliprabananthlal.ingest.Messages;

@Component
public class RateListener {

  private static final Logger LOG = LoggerFactory.getLogger(RateListener.class);

  private static final String UPDATE_THE_RATE =
      "UPDATE fx_rate SET rate = ?, where_from = 'LIVE TICK'"
          + " WHERE from_currency = ? AND to_currency = ? AND rate_date = CURRENT_DATE";

  private final JdbcTemplate database;
  private final Messages messages;

  private long howManyRowsChangedSoFar;

  public RateListener(JdbcTemplate database, Messages messages) {
    this.database = database;
    this.messages = messages;
  }

  @KafkaListener(topics = "rtat.fx-rate", groupId = "rate-receiver")
  public void whenRatesArrive(List<String> batch, Acknowledgment kafka) {
    List<Object[]> rowsToWrite = batch.stream().map(this::asRow).toList();

    int[] whatTheDatabaseReported = database.batchUpdate(UPDATE_THE_RATE, rowsToWrite);

    kafka.acknowledge();

    howManyRowsChangedSoFar =
        howManyRowsChangedSoFar + messages.howManyRowsChanged(whatTheDatabaseReported);
    LOG.info("rates changed so far: {}", howManyRowsChangedSoFar);
  }

  private Object[] asRow(String message) {
    JsonNode fields = messages.read(message);

    return new Object[] {
      fields.path("rate").asDouble(), fields.path("from").asText(), fields.path("to").asText()
    };
  }
}
