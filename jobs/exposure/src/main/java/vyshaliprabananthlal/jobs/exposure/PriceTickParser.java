package vyshaliprabananthlal.jobs.exposure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Serializable;
import org.apache.flink.api.common.functions.MapFunction;

public class PriceTickParser implements MapFunction<String, PriceTick>, Serializable {

  private static final long serialVersionUID = 1L;

  private static final ThreadLocal<ObjectMapper> JSON = ThreadLocal.withInitial(ObjectMapper::new);

  @Override
  public PriceTick map(String message) throws Exception {
    JsonNode fields = JSON.get().readTree(message);

    return new PriceTick(fields.path("productId").asInt(), fields.path("price").asDouble());
  }
}
