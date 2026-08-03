package vyshaliprabananthlal.common.money;

import java.io.Serializable;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.jspecify.annotations.Nullable;

/**
 * A currency the platform can hold exposure in.
 *
 * <p>Most codes are ISO 4217 and are validated against the JDK registry. FX desks also trade a few
 * codes ISO does not list - most importantly CNH, the offshore deliverable yuan, which prices and
 * settles separately from onshore CNY. The exposure grid shows CNH/CNY as a pair, so rejecting CNH
 * would make the tool unusable. Those exceptions live in {@link #NON_ISO}.
 *
 * <p>Instances are interned. There are only a couple of hundred currencies but tens of millions of
 * positions a day, so validating and looking up fraction digits on every construction was pure
 * waste - and the ISO check used a thrown exception as control flow, which is expensive. {@link
 * #of(String)} resolves that work once per currency, then hands back the shared instance.
 */
public final class Ccy implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Non-ISO codes the platform accepts, mapped to their quoting decimal places. */
  private static final Map<String, Integer> NON_ISO = Map.of("CNH", 2);

  private static final ConcurrentMap<String, Ccy> INTERNED = new ConcurrentHashMap<>();

  private final String code;
  private final int minorUnits;

  private Ccy(String code, int minorUnits) {
    this.code = code;
    this.minorUnits = minorUnits;
  }

  /**
   * Returns the shared instance for the given currency code.
   *
   * @param code a currency code in any case, optionally padded. Null is accepted and rejected with
   *     {@link IllegalArgumentException} rather than a {@link NullPointerException}, because this
   *     is a parsing boundary: bad upstream data should fail as bad input, not as a bug.
   */
  public static Ccy of(@Nullable String code) {
    String normalised = normalise(code);

    // Plain get before computeIfAbsent: the map holds every currency the system
    // will ever see within seconds of startup, so this is the path taken
    // essentially every time and it avoids computeIfAbsent's bin locking.
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

  /**
   * Decimal places this currency quotes to: JPY 0, USD 2, KWD 3. Used when rounding for settlement
   * or display - never for intermediate arithmetic.
   */
  public int minorUnits() {
    return minorUnits;
  }

  /**
   * Returns the interned instance so deserialized currencies stay reference-comparable. Without
   * this, every deserialized position would carry its own Ccy object and the equality fast path
   * would be lost exactly where it matters most.
   */
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
