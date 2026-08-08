package vyshaliprabananthlal.jobs.exposure;

import java.util.List;
import java.util.Map;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

public final class ExposureJob {

    static final String PRICES_COME_FROM = "rtat.price";
    static final String TOTALS_GO_TO = "rtat.exposure";

    private ExposureJob() {}

    public static void main(String[] args) throws Exception {
        String kafka = setting("RTAT_KAFKA", "localhost:9092");
        String databaseUrl = setting("RTAT_DB_URL", "jdbc:postgresql://localhost:5432/rtat");
        String user = setting("RTAT_DB_USER", "rtat");
        String password = System.getenv("RTAT_DB_PASSWORD");

        if (password == null || password.isBlank()) {
            throw new IllegalStateException("RTAT_DB_PASSWORD is not set");
        }

        Map<Integer, List<ExposureMessages.FundHolding>> whoHoldsWhat =
                HoldingsLoader.from(databaseUrl, user, password);

        System.out.println("loaded "
                + HoldingsLoader.holdingCount(whoHoldsWhat)
                + " holdings across "
                + whoHoldsWhat.size()
                + " securities");

        StreamExecutionEnvironment flink = StreamExecutionEnvironment.getExecutionEnvironment();

        KafkaSource<String> prices = KafkaSource.<String>builder()
                .setBootstrapServers(kafka)
                .setTopics(PRICES_COME_FROM)
                .setGroupId("exposure-job")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        KafkaSink<String> totals = KafkaSink.<String>builder()
                .setBootstrapServers(kafka)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(TOTALS_GO_TO)
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                .build();

        DataStream<String> asText = flink.fromSource(prices, WatermarkStrategy.noWatermarks(), "prices from kafka");

        asText.map(new PriceTickParser())
                .keyBy(ExposureMessages.PriceTick::productId)
                .process(new PriceDeltaFunction(whoHoldsWhat))
                .keyBy(ExposureMessages.ExposureDelta::key)
                .process(new RunningTotalFunction())
                .map(ExposureMessages.RunningTotal::asMessage)
                .sinkTo(totals);

        flink.execute("exposure from prices");
    }

    private static String setting(String name, String whenNotSet) {
        return System.getenv().getOrDefault(name, whenNotSet);
    }
}
