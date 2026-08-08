package vyshaliprabananthlal.calculate.hedge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vyshaliprabananthlal.calculate.exposure.FundExposure;

class HedgeAdviserTest {

    private final HedgeAdviser adviser = new HedgeAdviser(100000);

    @Test
    @DisplayName("a foreign currency the fund is holding is suggested for selling")
    void aHoldingIsSuggestedForSelling() {
        List<HedgeAdviser.Recommendation> advice =
                adviser.recommendFor(usdFundHolding(new FundExposure.CurrencyAmount("EUR", 5000000, 5500000)));

        assertThat(advice).hasSize(1);
        assertThat(advice.get(0).currency()).isEqualTo("EUR");
        assertThat(advice.get(0).suggestedAmount()).isEqualTo(-5000000.0);
        assertThat(advice.get(0).instrument()).isEqualTo("FORWARD");
    }

    @Test
    @DisplayName("the currency the fund reports in is never hedged against itself")
    void theReportingCurrencyIsNeverHedged() {
        List<HedgeAdviser.Recommendation> advice =
                adviser.recommendFor(usdFundHolding(new FundExposure.CurrencyAmount("USD", 9000000, 9000000)));

        assertThat(advice).isEmpty();
    }

    @Test
    @DisplayName("an amount too small to be worth a trade is left alone")
    void aTinyExposureIsLeftAlone() {
        List<HedgeAdviser.Recommendation> advice =
                adviser.recommendFor(usdFundHolding(new FundExposure.CurrencyAmount("EUR", 50000, 55000)));

        assertThat(advice).isEmpty();
    }

    @Test
    @DisplayName("a short position is suggested for buying, the opposite way round")
    void aShortIsSuggestedForBuying() {
        List<HedgeAdviser.Recommendation> advice =
                adviser.recommendFor(usdFundHolding(new FundExposure.CurrencyAmount("JPY", -800000000, -5120000)));

        assertThat(advice.get(0).suggestedAmount()).isEqualTo(800000000.0);
        assertThat(advice.get(0).reason()).contains("short");
    }

    @Test
    @DisplayName("the size test uses the reporting currency, not the raw foreign amount")
    void theSizeTestUsesTheReportingCurrency() {
        List<HedgeAdviser.Recommendation> advice =
                adviser.recommendFor(usdFundHolding(new FundExposure.CurrencyAmount("JPY", 10000000, 64000)));

        assertThat(advice).isEmpty();
    }

    @Test
    @DisplayName("every currency worth hedging gets its own recommendation")
    void eachCurrencyGetsItsOwn() {
        FundExposure exposure = new FundExposure(
                1,
                "USD",
                List.of(
                        new FundExposure.CurrencyAmount("EUR", 5000000, 5500000),
                        new FundExposure.CurrencyAmount("GBP", 2000000, 2500000),
                        new FundExposure.CurrencyAmount("USD", 1000000, 1000000)),
                4);

        List<HedgeAdviser.Recommendation> advice = adviser.recommendFor(exposure);

        assertThat(advice).hasSize(2);
        assertThat(advice.stream().map(HedgeAdviser.Recommendation::currency)).containsExactly("EUR", "GBP");
    }

    @Test
    @DisplayName("the reason says which way round it is, so a person can check it")
    void theReasonExplainsItself() {
        List<HedgeAdviser.Recommendation> advice =
                adviser.recommendFor(usdFundHolding(new FundExposure.CurrencyAmount("EUR", 5000000, 5500000)));

        assertThat(advice.get(0).reason())
                .contains("reports in USD")
                .contains("holding")
                .contains("EUR");
    }

    private FundExposure usdFundHolding(FundExposure.CurrencyAmount one) {
        return new FundExposure(1, "USD", List.of(one), 1);
    }
}
