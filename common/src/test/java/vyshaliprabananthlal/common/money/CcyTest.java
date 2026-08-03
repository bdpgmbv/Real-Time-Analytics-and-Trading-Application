package vyshaliprabananthlal.common.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CcyTest {

  @Test
  void normalisesCaseAndWhitespace() {
    assertThat(Ccy.of(" usd ").code()).isEqualTo("USD");
  }

  @Test
  @DisplayName("accepts CNH even though it is not ISO 4217")
  void acceptsOffshoreYuan() {
    Ccy cnh = Ccy.of("CNH");

    assertThat(cnh.code()).isEqualTo("CNH");
    assertThat(cnh.minorUnits()).isEqualTo(2);
  }

  @Test
  void reportsMinorUnitsPerCurrency() {
    assertThat(Ccy.of("USD").minorUnits()).isEqualTo(2);
    assertThat(Ccy.of("JPY").minorUnits()).isZero();
    assertThat(Ccy.of("KWD").minorUnits()).isEqualTo(3);
  }

  @Test
  @DisplayName("the same code always yields the same instance")
  void internsInstances() {
    assertThat(Ccy.of("EUR")).isSameAs(Ccy.of("eur"));
  }

  @Test
  @DisplayName("a deserialized currency is still the interned instance")
  void serialisationPreservesInterning() throws Exception {
    Ccy original = Ccy.of("JPY");

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(original);
    }
    Object restored;
    try (ObjectInputStream in =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = in.readObject();
    }

    assertThat(restored).isSameAs(original);
  }

  @Test
  void equalityHandlesSelfNullAndForeignTypes() {
    Ccy usd = Ccy.of("USD");

    // Typed as Object deliberately: the equals contract must reject a foreign type at
    // runtime, and a String literal here would just be a compile-time error instead.
    Object foreignType = "USD";

    assertThat(usd.equals(usd)).isTrue();
    assertThat(usd.equals(null)).isFalse();
    assertThat(usd.equals(foreignType)).isFalse();
    assertThat(usd).hasSameHashCodeAs(Ccy.of("usd"));
  }

  @Test
  void rejectsUnknownAndMalformedCodes() {
    assertThatThrownBy(() -> Ccy.of("ZZZ")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Ccy.of("US")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Ccy.of(null)).isInstanceOf(IllegalArgumentException.class);
  }
}
