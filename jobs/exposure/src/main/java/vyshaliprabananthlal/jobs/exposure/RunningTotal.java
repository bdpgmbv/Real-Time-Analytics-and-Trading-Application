package vyshaliprabananthlal.jobs.exposure;

import java.io.Serializable;

public record RunningTotal(int fundId, String currency, double total) implements Serializable {

  public String asMessage() {
    return String.format(
        "{\"fundId\":%d,\"currency\":\"%s\",\"exposure\":%s}",
        fundId, currency, Math.round(total * 10000) / 10000.0);
  }
}
