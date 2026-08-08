package vyshaliprabananthlal.ingest.receive;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import vyshaliprabananthlal.ingest.message.JsonReader;
import vyshaliprabananthlal.platform.sql.SqlStatements;

@Component
public class TradeListener {

    private static final Logger LOG = LoggerFactory.getLogger(TradeListener.class);

    private final KafkaBatch batch;
    private final JsonReader messages;
    private final String statement;

    private long howManyTradesRecorded;

    public TradeListener(KafkaBatch batch, JsonReader messages, SqlStatements statements) {
        this.batch = batch;
        this.messages = messages;
        this.statement = statements.statement("insert-trade-and-update-position");
    }

    @KafkaListener(topics = "rtat.trade", groupId = "trade-receiver")
    public void arrived(List<String> arrived, Acknowledgment kafka) {
        List<Object[]> rows = arrived.stream().map(this::asRow).toList();

        howManyTradesRecorded = howManyTradesRecorded + batch.writeThenAcknowledge("trade", statement, rows, kafka);

        LOG.info("trades recorded so far: {}", howManyTradesRecorded);
    }

    private Object[] asRow(String message) {
        JsonNode fields = messages.read(message);
        Timestamp happenedAt =
                Timestamp.from(Instant.parse(fields.path("happenedAt").asText()));

        return new Object[] {
            fields.path("tradeId").asLong(),
            fields.path("accountId").asInt(),
            fields.path("productId").asInt(),
            fields.path("howMany").asDouble(),
            fields.path("price").asDouble(),
            happenedAt,
            happenedAt,
            fields.path("cameFrom").asText()
        };
    }
}
