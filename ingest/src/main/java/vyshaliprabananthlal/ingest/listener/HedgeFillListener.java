package vyshaliprabananthlal.ingest.listener;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import vyshaliprabananthlal.ingest.Messages;

@Component
public class HedgeFillListener {

  private static final Logger LOG = LoggerFactory.getLogger(HedgeFillListener.class);

  private static final String RECORD_THE_FILL =
      "INSERT INTO hedge_fill"
          + " (fill_id, hedge_id, amount_filled, rate_we_got, filled_at, their_reference)"
          + " VALUES (?, ?, ?, ?, ?, ?)"
          + " ON CONFLICT (fill_id) DO NOTHING";

  private static final String MOVE_THE_HEDGE_ON =
      "UPDATE hedge SET status = CASE"
          + "   WHEN (SELECT coalesce(sum(amount_filled), 0) FROM hedge_fill f"
          + "          WHERE f.hedge_id = hedge.hedge_id) >= abs(hedge.client_chose)"
          + "   THEN 'FILLED' ELSE 'PARTIALLY FILLED' END"
          + " WHERE hedge_id = ?";

  private final JdbcTemplate database;
  private final Messages messages;

  private long howManyFillsRecorded;

  public HedgeFillListener(JdbcTemplate database, Messages messages) {
    this.database = database;
    this.messages = messages;
  }

  @KafkaListener(topics = "rtat.hedge-fill", groupId = "hedge-fill-receiver")
  public void whenFillsArrive(List<String> batch, Acknowledgment kafka) {
    List<Object[]> fills = batch.stream().map(this::asRow).toList();

    int[] whatTheDatabaseReported = database.batchUpdate(RECORD_THE_FILL, fills);

    for (Object[] fill : fills) {
      database.update(MOVE_THE_HEDGE_ON, fill[1]);
    }

    kafka.acknowledge();

    howManyFillsRecorded =
        howManyFillsRecorded + messages.howManyRowsChanged(whatTheDatabaseReported);
    LOG.info("fills recorded so far: {}", howManyFillsRecorded);
  }

  private Object[] asRow(String message) {
    JsonNode fields = messages.read(message);

    return new Object[] {
      fields.path("fillId").asLong(),
      fields.path("hedgeId").asLong(),
      fields.path("amountFilled").asDouble(),
      fields.path("rate").asDouble(),
      Timestamp.from(Instant.parse(fields.path("filledAt").asText())),
      fields.path("theirReference").asText()
    };
  }
}
