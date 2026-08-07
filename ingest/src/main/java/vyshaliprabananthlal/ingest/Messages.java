package vyshaliprabananthlal.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class Messages {

  private final ObjectMapper json = new ObjectMapper();

  public JsonNode read(String message) {
    try {
      return json.readTree(message);
    } catch (Exception problem) {
      throw new IllegalStateException("could not read message: " + message, problem);
    }
  }

  public int howManyRowsChanged(int[] whatTheDatabaseReported) {
    int total = 0;
    for (int rowsChanged : whatTheDatabaseReported) {
      total = total + Math.max(0, rowsChanged);
    }
    return total;
  }
}
