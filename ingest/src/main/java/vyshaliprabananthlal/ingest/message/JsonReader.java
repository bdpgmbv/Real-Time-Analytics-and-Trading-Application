package vyshaliprabananthlal.ingest.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class JsonReader {

  private final ObjectMapper json = new ObjectMapper();

  public JsonNode read(String message) {
    try {
      return json.readTree(message);
    } catch (Exception problem) {
      throw new IllegalStateException("could not read message: " + message, problem);
    }
  }
}
