package vyshaliprabananthlal.common.money;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(Ccy ccy, BigDecimal amount) implements Serializable {
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

  public boolean isEqualValue(Money other) {
    requireSameCcy(other);
    return amount.compareTo(other.amount) == 0;
  }

  private void requireSameCcy(Money other) {
    Objects.requireNonNull(other, "other");
    if (!ccy.equals(other.ccy)) {
      throw new IllegalArgumentException(
          "Currency mismatch: "
              + ccy
              + " vs "
              + other.ccy
              + ". Convert with an FX rate before combining.");
    }
  }

  @Override
  public String toString() {
    return amount.toPlainString() + " " + ccy;
  }
}
