package vyshaliprabananthlal.ingest.receive;

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
import vyshaliprabananthlal.ingest.message.JsonReader;
import vyshaliprabananthlal.platform.sql.SqlStatements;

@Component
public class HedgeFillListener {

    private static final Logger LOG = LoggerFactory.getLogger(HedgeFillListener.class);

    private final JdbcTemplate database;
    private final KafkaBatch batch;
    private final JsonReader messages;
    private final String recordTheFill;
    private final String moveTheHedgeOn;

    private long howManyFillsRecorded;

    public HedgeFillListener(JdbcTemplate database, KafkaBatch batch, JsonReader messages, SqlStatements statements) {

        this.database = database;
        this.batch = batch;
        this.messages = messages;
        this.recordTheFill = statements.statement("insert-hedge-fill");
        this.moveTheHedgeOn = statements.statement("update-hedge-status");
    }

    @KafkaListener(topics = "rtat.hedge-fill", groupId = "hedge-fill-receiver")
    public void arrived(List<String> arrived, Acknowledgment kafka) {
        List<Object[]> fills = arrived.stream().map(this::asRow).toList();

        int recorded = batch.write("hedge-fill", recordTheFill, fills);

        for (Object[] fill : fills) {
            database.update(moveTheHedgeOn, fill[1]);
        }

        batch.acknowledge(kafka);

        howManyFillsRecorded = howManyFillsRecorded + recorded;

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
