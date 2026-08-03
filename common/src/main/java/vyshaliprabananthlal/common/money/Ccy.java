package vyshaliprabananthlal.common.money;

import java.io.Serializable;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;

/**
 * A currency the platform can hold exposure in.
 *
 * <p>Most codes are ISO 4217 and are validated against the JDK registry. FX desks also trade a
 * few codes ISO does not list - most importantly CNH, the offshore deliverable yuan, which
 * prices and settles separately from onshore CNY. The exposure grid shows CNH/CNY as a pair, so
 * rejecting CNH would make the tool unusable. Those exceptions live in {@link #NON_ISO}.
 */
public record Ccy(String code) implements Serializable {

    /** Non-ISO codes the platform accepts, mapped to their quoting decimal places. */
    private static final Map<String, Integer> NON_ISO = Map.of("CNH", 2);

    public Ccy {
        if (code == null) {
            throw new IllegalArgumentException("Currency code must not be null");
        }
        code = code.trim().toUpperCase(Locale.ROOT);
        if (code.length() != 3) {
            throw new IllegalArgumentException("Currency code must be 3 letters, got: '" + code + "'");
        }
        if (!isKnown(code)) {
            throw new IllegalArgumentException("Unknown currency code: " + code);
        }
    }

    public static Ccy of(String code) {
        return new Ccy(code);
    }

    private static boolean isKnown(String upperCode) {
        if (NON_ISO.containsKey(upperCode)) {
            return true;
        }
        try {
            Currency.getInstance(upperCode);
            return true;
        } catch (IllegalArgumentException notIso) {
            return false;
        }
    }

    /**
     * Decimal places this currency quotes to: JPY 0, USD 2, KWD 3. Used when rounding for
     * settlement or display - never for intermediate arithmetic.
     */
    public int minorUnits() {
        Integer nonIso = NON_ISO.get(code);
        if (nonIso != null) {
            return nonIso;
        }
        return Currency.getInstance(code).getDefaultFractionDigits();
    }

    @Override
    public String toString() {
        return code;
    }
}
