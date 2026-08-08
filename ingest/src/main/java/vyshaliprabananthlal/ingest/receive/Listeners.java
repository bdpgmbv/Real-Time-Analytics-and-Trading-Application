package vyshaliprabananthlal.ingest.receive;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import vyshaliprabananthlal.ingest.message.JsonReader;
import vyshaliprabananthlal.platform.sql.SqlStatements;

/**
 * The three feeds that only ever update a row that already exists.
 *
 * <p>They are together because they are the same shape: read a batch, turn each message into
 * parameters, write, then tell Kafka. The only differences are the topic, the statement, and
 * which fields to pull out.
 *
 * <p>Trades and hedge fills are not here. They insert as well as update, and that makes them a
 * different problem.
 */
public final class Listeners {

    private static final Logger LOG = LoggerFactory.getLogger(Listeners.class);

    private Listeners() {}

    @Component
    public static class Positions {

        private final KafkaBatch batch;
        private final JsonReader json;
        private final String statement;
        private long rowsChangedSoFar;

        public Positions(KafkaBatch batch, JsonReader json, SqlStatements statements) {
            this.batch = batch;
            this.json = json;
            this.statement = statements.statement("update-position");
        }

        @KafkaListener(topics = "rtat.position", groupId = "position-receiver")
        public void arrived(List<String> messages, Acknowledgment kafka) {
            List<Object[]> rows = messages.stream().map(this::asRow).toList();

            rowsChangedSoFar += batch.writeThenAcknowledge("position", statement, rows, kafka);
            LOG.info("positions changed so far: {}", rowsChangedSoFar);
        }

        private Object[] asRow(String message) {
            JsonNode f = json.read(message);
            return new Object[] {
                f.path("howMany").asDouble(),
                f.path("accountId").asInt(),
                f.path("productId").asInt()
            };
        }
    }

    @Component
    public static class Prices {

        private final KafkaBatch batch;
        private final JsonReader json;
        private final String statement;
        private long rowsChangedSoFar;

        public Prices(KafkaBatch batch, JsonReader json, SqlStatements statements) {
            this.batch = batch;
            this.json = json;
            this.statement = statements.statement("update-price");
        }

        @KafkaListener(topics = "rtat.price", groupId = "price-receiver")
        public void arrived(List<String> messages, Acknowledgment kafka) {
            List<Object[]> rows = messages.stream().map(this::asRow).toList();

            rowsChangedSoFar += batch.writeThenAcknowledge("price", statement, rows, kafka);
            LOG.info("prices changed so far: {}", rowsChangedSoFar);
        }

        private Object[] asRow(String message) {
            JsonNode f = json.read(message);
            return new Object[] {f.path("price").asDouble(), f.path("productId").asInt()};
        }
    }

    @Component
    public static class Rates {

        private final KafkaBatch batch;
        private final JsonReader json;
        private final String statement;
        private long rowsChangedSoFar;

        public Rates(KafkaBatch batch, JsonReader json, SqlStatements statements) {
            this.batch = batch;
            this.json = json;
            this.statement = statements.statement("update-fx-rate");
        }

        @KafkaListener(topics = "rtat.fx-rate", groupId = "rate-receiver")
        public void arrived(List<String> messages, Acknowledgment kafka) {
            List<Object[]> rows = messages.stream().map(this::asRow).toList();

            rowsChangedSoFar += batch.writeThenAcknowledge("rate", statement, rows, kafka);
            LOG.info("rates changed so far: {}", rowsChangedSoFar);
        }

        private Object[] asRow(String message) {
            JsonNode f = json.read(message);
            return new Object[] {
                f.path("rate").asDouble(), f.path("from").asText(), f.path("to").asText()
            };
        }
    }
}
