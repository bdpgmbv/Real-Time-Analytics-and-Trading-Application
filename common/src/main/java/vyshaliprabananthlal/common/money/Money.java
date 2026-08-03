package vyshaliprabananthlal.common.money;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An amount in a single currency.
 *
 * <p>Always {@link BigDecimal}, never {@code double}. A fund's exposure runs to eleven figures
 * and binary floating point loses cents at that magnitude.
 *
 * <p>Arithmetic here never rounds. A valuation chain - price x quantity x FX rate x weight -
 * rounds once at the end via {@link #roundToMinorUnits()}. Rounding at each step accumulates
 * drift that surfaces as a phantom unhedged balance on large notionals.
 */
public record Money(Ccy ccy, BigDecimal amount) implements Serializable {

    /** Division precision: 34 significant digits, i.e. IEEE 754-2008 decimal128. */
    public static final MathContext DIVISION = new MathContext(34, RoundingMode.HALF_EVEN);

    public Money {
        Objects.requireNonNull(ccy, "ccy");
        Objects.requireNonNull(amount, "amount");
    }

    public static Money of(Ccy ccy, BigDecimal amount) {
        return new Money(ccy, amount);
    }

    public static Money of(String ccyCode, String amount) {
        return new Money(Ccy.of(ccyCode), new BigDecimal(amount));
    }

    public static Money zero(Ccy ccy) {
        return new Money(ccy, BigDecimal.ZERO);
    }

    public Money plus(Money other) {
        requireSameCcy(other);
        return new Money(ccy, amount.add(other.amount));
    }

    public Money minus(Money other) {
        requireSameCcy(other);
        return new Money(ccy, amount.subtract(other.amount));
    }

    public Money negated() {
        return new Money(ccy, amount.negate());
    }

    /** Scales by a dimensionless factor: a quantity, an exposure weight, a hedge ratio. */
    public Money times(BigDecimal factor) {
        Objects.requireNonNull(factor, "factor");
        return new Money(ccy, amount.multiply(factor));
    }

    public Money dividedBy(BigDecimal divisor) {
        Objects.requireNonNull(divisor, "divisor");
        if (divisor.signum() == 0) {
            throw new ArithmeticException("Division by zero on " + this);
        }
        return new Money(ccy, amount.divide(divisor, DIVISION));
    }

    /** Rounds half-even to the currency's minor units. Call once, at the boundary. */
    public Money roundToMinorUnits() {
        int scale = ccy.minorUnits();
        BigDecimal rounded = amount.setScale(scale, RoundingMode.HALF_EVEN);
        return new Money(ccy, rounded);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public int signum() {
        return amount.signum();
    }

    /**
     * Value equality ignoring scale: 100 USD and 100.00 USD are the same money. The record's
     * generated {@code equals} delegates to {@link BigDecimal#equals}, which is scale-sensitive
     * and would call those two unequal. Use this for domain comparisons.
     */
    public boolean isEqualValue(Money other) {
        requireSameCcy(other);
        return amount.compareTo(other.amount) == 0;
    }

    private void requireSameCcy(Money other) {
        Objects.requireNonNull(other, "other");
        if (!ccy.equals(other.ccy)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: " + ccy + " vs " + other.ccy
                            + ". Convert with an FX rate before combining.");
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + ccy;
    }
}
