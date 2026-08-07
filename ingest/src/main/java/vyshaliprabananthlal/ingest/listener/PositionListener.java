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
public class PositionListener {

  private static final Logger LOG = LoggerFactory.getLogger(PositionListener.class);

  private static final String UPDATE_THE_POSITION =
      "UPDATE position SET how_many = ? WHERE account_id = ? AND product_id = ?";

  private final JdbcTemplate database;
  private final Messages messages;

  private long howManyRowsChangedSoFar;

  public PositionListener(JdbcTemplate database, Messages messages) {
    this.database = database;
    this.messages = messages;
  }

  @KafkaListener(topics = "rtat.position", groupId = "position-receiver")
  public void whenPositionsArrive(List<String> batch, Acknowledgment kafka) {
    List<Object[]> rowsToWrite = batch.stream().map(this::asRow).toList();

    int[] whatTheDatabaseReported = database.batchUpdate(UPDATE_THE_POSITION, rowsToWrite);

    kafka.acknowledge();

    howManyRowsChangedSoFar =
        howManyRowsChangedSoFar + messages.howManyRowsChanged(whatTheDatabaseReported);
    LOG.info("positions changed so far: {}", howManyRowsChangedSoFar);
  }

  private Object[] asRow(String message) {
    JsonNode fields = messages.read(message);

    return new Object[] {
      fields.path("howMany").asDouble(),
      fields.path("accountId").asInt(),
      fields.path("productId").asInt()
    };
  }
}
