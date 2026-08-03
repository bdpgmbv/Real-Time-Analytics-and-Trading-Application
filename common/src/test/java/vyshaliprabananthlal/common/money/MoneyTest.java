package vyshaliprabananthlal.common.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MoneyTest {

  @Test
  void addsAndSubtractsWithinOneCurrency() {
    Money a = Money.of("EUR", "100000.00");
    Money b = Money.of("EUR", "5000.00");

    assertThat(a.plus(b).isEqualValue(Money.of("EUR", "105000"))).isTrue();
    assertThat(a.minus(b).isEqualValue(Money.of("EUR", "95000"))).isTrue();
  }

  @Test
  @DisplayName("mixing currencies fails loudly rather than silently summing")
  void rejectsCurrencyMismatch() {
    Money eur = Money.of("EUR", "100");
    Money usd = Money.of("USD", "100");

    assertThatThrownBy(() -> eur.plus(usd))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Currency mismatch: EUR vs USD");
  }

  @Test
  @DisplayName("multiplication keeps full precision so chained valuation does not drift")
  void timesDoesNotRound() {
    Money position = Money.of("EUR", "100000");

    Money valued = position.times(new BigDecimal("1.170000001"));

    assertThat(valued.amount()).isEqualByComparingTo("117000.0001");
  }

  @Test
  void roundsHalfEvenToMinorUnits() {
    assertThat(Money.of("USD", "1.005").roundToMinorUnits().amount()).isEqualByComparingTo("1.00");
    assertThat(Money.of("USD", "1.015").roundToMinorUnits().amount()).isEqualByComparingTo("1.02");
    assertThat(Money.of("JPY", "2.5").roundToMinorUnits().amount()).isEqualByComparingTo("2");
    assertThat(Money.of("JPY", "3.5").roundToMinorUnits().amount()).isEqualByComparingTo("4");
  }

  @Test
  void divisionByZeroIsRejected() {
    assertThatThrownBy(() -> Money.of("USD", "100").dividedBy(BigDecimal.ZERO))
        .isInstanceOf(ArithmeticException.class);
  }

  @Test
  void reportsSignAndZero() {
    Money positive = Money.of("USD", "1.00");
    Money negative = Money.of("USD", "-1.00");
    Money zero = Money.zero(Ccy.of("USD"));

    assertThat(zero.isZero()).isTrue();
    assertThat(positive.isZero()).isFalse();
    assertThat(negative.isNegative()).isTrue();
    assertThat(positive.isNegative()).isFalse();
    assertThat(positive.signum()).isEqualTo(1);
    assertThat(negative.signum()).isEqualTo(-1);
    assertThat(zero.signum()).isZero();
  }

  @Test
  void negatesAndBuildsFromParts() {
    Money original = Money.of(Ccy.of("GBP"), new BigDecimal("42.50"));

    assertThat(original.negated().isEqualValue(Money.of("GBP", "-42.50"))).isTrue();
  }

  @Test
  @DisplayName("division carries 34 significant digits, enough for any FX cross")
  void dividesAtDecimal128Precision() {
    Money notional = Money.of("USD", "1000000");

    Money share = notional.dividedBy(new BigDecimal("3"));

    assertThat(share.amount().precision()).isEqualTo(34);
  }

  @Test
  void valueComparisonAlsoRejectsCurrencyMismatch() {
    Money usd = Money.of("USD", "1");
    Money eur = Money.of("EUR", "1");

    assertThatThrownBy(() -> usd.isEqualValue(eur))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Currency mismatch");
  }

  @Test
  @DisplayName("isEqualValue ignores scale where record equals does not")
  void valueEqualityIgnoresScale() {
    Money plain = Money.of("USD", "100");
    Money scaled = Money.of("USD", "100.00");

    assertThat(plain.isEqualValue(scaled)).isTrue();
    assertThat(plain).isNotEqualTo(scaled);
  }
}
