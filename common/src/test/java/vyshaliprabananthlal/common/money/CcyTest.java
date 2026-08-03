package vyshaliprabananthlal.common.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void rejectsUnknownAndMalformedCodes() {
        assertThatThrownBy(() -> Ccy.of("ZZZ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Ccy.of("US")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Ccy.of(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
