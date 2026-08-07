package vyshaliprabananthlal.ingest.listener;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import vyshaliprabananthlal.ingest.Messages;

@Component
public class TradeListener {

  private static final Logger LOG = LoggerFactory.getLogger(TradeListener.class);

  private static final String RECORD_THE_TRADE_AND_MOVE_THE_POSITION =
      "WITH newly_recorded AS ("
          + "  INSERT INTO trade"
          + "    (trade_id, account_id, product_id, how_many, price,"
          + "     happened_at, trade_date, came_from)"
          + "  VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
          + "  ON CONFLICT (trade_id) DO NOTHING"
          + "  RETURNING account_id, product_id, how_many"
          + ") "
          + "UPDATE position"
          + "   SET how_many = position.how_many + newly_recorded.how_many"
          + "  FROM newly_recorded"
          + " WHERE position.account_id = newly_recorded.account_id"
          + "   AND position.product_id = newly_recorded.product_id";

  private final JdbcTemplate database;
  private final Messages messages;

  private long howManyPositionsMovedSoFar;

  public TradeListener(JdbcTemplate database, Messages messages) {
    this.database = database;
    this.messages = messages;
  }

  @KafkaListener(topics = "rtat.trade", groupId = "trade-receiver")
  public void whenTradesArrive(List<String> batch, Acknowledgment kafka) {
    List<Object[]> rowsToWrite = batch.stream().map(this::asRow).toList();

    int[] whatTheDatabaseReported =
        database.batchUpdate(RECORD_THE_TRADE_AND_MOVE_THE_POSITION, rowsToWrite);

    kafka.acknowledge();

    howManyPositionsMovedSoFar =
        howManyPositionsMovedSoFar + messages.howManyRowsChanged(whatTheDatabaseReported);
    LOG.info("positions moved by a trade so far: {}", howManyPositionsMovedSoFar);
  }

  private Object[] asRow(String message) {
    JsonNode fields = messages.read(message);
    Instant happenedAt = Instant.parse(fields.path("happenedAt").asText());

    return new Object[] {
      fields.path("tradeId").asLong(),
      fields.path("accountId").asInt(),
      fields.path("productId").asInt(),
      fields.path("howMany").asDouble(),
      fields.path("price").asDouble(),
      Timestamp.from(happenedAt),
      happenedAt.atZone(ZoneOffset.UTC).toLocalDate(),
      fields.path("cameFrom").asText()
    };
  }
}
