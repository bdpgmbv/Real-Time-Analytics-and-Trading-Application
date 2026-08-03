package vyshaliprabananthlal.contract.codec;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vyshaliprabananthlal.common.money.Money;
import vyshaliprabananthlal.contract.v1.Decimal;

class DecimalCodecTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "0",
            "1",
            "-1",
            "0.00",
            "121012951.50",              // a real exposure figure from the legacy grid
            "-9038572.29",
            "3676923340.00",             // JPY notional
            "1765848971.54",
            "0.000000000001",
            "-99999999999999999999.999999999999"
    })
    @DisplayName("round-trips exactly, preserving scale")
    void roundTripsExactly(String literal) {
        BigDecimal original = new BigDecimal(literal);

        BigDecimal restored = DecimalCodec.fromProto(DecimalCodec.toProto(original));

        assertThat(restored).isEqualTo(original);
        assertThat(restored.scale()).isEqualTo(original.scale());
    }

    @Test
    @DisplayName("a default-constructed Decimal decodes as zero rather than throwing")
    void emptyUnscaledIsZero() {
        BigDecimal decoded = DecimalCodec.fromProto(Decimal.getDefaultInstance());

        assertThat(decoded).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void roundTripsMoneyIncludingNonIsoCurrency() {
        Money original = Money.of("CNH", "121029976.30");

        Money restored = DecimalCodec.fromProto(DecimalCodec.toProto(original));

        assertThat(restored.ccy().code()).isEqualTo("CNH");
        assertThat(restored.isEqualValue(original)).isTrue();
    }
}
