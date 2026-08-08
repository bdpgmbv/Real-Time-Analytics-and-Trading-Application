package vyshaliprabananthlal.ingest.receive;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import vyshaliprabananthlal.ingest.message.JsonReader;
import vyshaliprabananthlal.ingest.sql.Sql;

@Component
public class PositionListener {

  private static final Logger LOG = LoggerFactory.getLogger(PositionListener.class);

  private final KafkaBatch batch;
  private final JsonReader messages;
  private final String statement;

  private long howManyRowsChangedSoFar;

  public PositionListener(KafkaBatch batch, JsonReader messages, Sql sql) {
    this.batch = batch;
    this.messages = messages;
    this.statement = sql.statement("update-position");
  }

  @KafkaListener(topics = "rtat.position", groupId = "position-receiver")
  public void whenPositionsArrive(List<String> arrived, Acknowledgment kafka) {
    List<Object[]> rows = arrived.stream().map(this::asRow).toList();

    howManyRowsChangedSoFar =
        howManyRowsChangedSoFar + batch.writeThenAcknowledge("position", statement, rows, kafka);

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
