package vyshaliprabananthlal.common.money;

import java.io.Serializable;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.jspecify.annotations.Nullable;

public final class Ccy implements Serializable {
  private static final long serialVersionUID = 1L;

  private static final Map<String, Integer> NON_ISO = Map.of("CNH", 2);

  private static final ConcurrentMap<String, Ccy> INTERNED = new ConcurrentHashMap<>();

  private final String code;
  private final int minorUnits;

  private Ccy(String code, int minorUnits) {
    this.code = code;
    this.minorUnits = minorUnits;
  }

  public static Ccy of(@Nullable String code) {
    String normalised = normalise(code);

    Ccy existing = INTERNED.get(normalised);
    if (existing != null) {
      return existing;
    }
    return INTERNED.computeIfAbsent(normalised, Ccy::create);
  }

  private static String normalise(@Nullable String code) {
    if (code == null) {
      throw new IllegalArgumentException("Currency code must not be null");
    }
    String trimmed = code.trim().toUpperCase(Locale.ROOT);
    if (trimmed.length() != 3) {
      throw new IllegalArgumentException("Currency code must be 3 letters, got: '" + trimmed + "'");
    }
    return trimmed;
  }

  private static Ccy create(String normalisedCode) {
    Integer nonIso = NON_ISO.get(normalisedCode);
    if (nonIso != null) {
      return new Ccy(normalisedCode, nonIso);
    }
    try {
      Currency iso = Currency.getInstance(normalisedCode);
      return new Ccy(normalisedCode, iso.getDefaultFractionDigits());
    } catch (IllegalArgumentException notIso) {
      throw new IllegalArgumentException("Unknown currency code: " + normalisedCode, notIso);
    }
  }

  public String code() {
    return code;
  }

  public int minorUnits() {
    return minorUnits;
  }

  private Object readResolve() {
    return of(code);
  }

  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof Ccy that)) {
      return false;
    }
    return code.equals(that.code);
  }

  @Override
  public int hashCode() {
    return code.hashCode();
  }

  @Override
  public String toString() {
    return code;
  }
}
