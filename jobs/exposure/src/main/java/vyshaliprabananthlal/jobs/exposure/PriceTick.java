package vyshaliprabananthlal.jobs.exposure;

import java.io.Serializable;

public record PriceTick(int productId, double price) implements Serializable {}
