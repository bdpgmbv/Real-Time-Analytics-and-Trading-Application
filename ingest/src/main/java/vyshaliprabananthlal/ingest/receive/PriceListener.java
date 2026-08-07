package vyshaliprabananthlal.ingest.receive;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import vyshaliprabananthlal.ingest.message.Messages;
import vyshaliprabananthlal.ingest.sql.Sql;

@Component
public class PriceListener {

  private static final Logger LOG = LoggerFactory.getLogger(PriceListener.class);

  private final KafkaBatch batch;
  private final Messages messages;
  private final String statement;

  private long howManyRowsChangedSoFar;

  public PriceListener(KafkaBatch batch, Messages messages, Sql sql) {
    this.batch = batch;
    this.messages = messages;
    this.statement = sql.statement("update-price");
  }

  @KafkaListener(topics = "rtat.price", groupId = "price-receiver")
  public void whenPricesArrive(List<String> arrived, Acknowledgment kafka) {
    List<Object[]> rows = arrived.stream().map(this::asRow).toList();

    howManyRowsChangedSoFar =
        howManyRowsChangedSoFar + batch.writeThenAcknowledge("price", statement, rows, kafka);

    LOG.info("prices changed so far: {}", howManyRowsChangedSoFar);
  }

  private Object[] asRow(String message) {
    JsonNode fields = messages.read(message);

    return new Object[] {fields.path("price").asDouble(), fields.path("productId").asInt()};
  }
}
