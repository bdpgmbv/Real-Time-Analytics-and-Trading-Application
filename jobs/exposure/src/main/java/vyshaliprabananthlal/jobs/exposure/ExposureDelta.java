package vyshaliprabananthlal.jobs.exposure;

import java.io.Serializable;

public record ExposureDelta(int fundId, String currency, double changeBy) implements Serializable {

  public String key() {
    return fundId + "|" + currency;
  }
}
