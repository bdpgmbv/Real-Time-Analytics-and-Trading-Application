package vyshaliprabananthlal.jobs.exposure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.operators.KeyedProcessOperator;
import org.apache.flink.streaming.util.KeyedOneInputStreamOperatorTestHarness;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KeepTheRunningTotalTest {

    @Test
    @DisplayName("the first difference becomes the total")
    void theFirstDifferenceIsTheTotal() throws Exception {
        try (var harness = started()) {
            harness.processElement(new ExposureMessages.ExposureDelta(1, "EUR", 600), 1);

            assertThat(totals(harness)).containsExactly(new ExposureMessages.RunningTotal(1, "EUR", 600.0));
        }
    }

    @Test
    @DisplayName("differences add up instead of replacing each other")
    void differencesAddUp() throws Exception {
        try (var harness = started()) {
            harness.processElement(new ExposureMessages.ExposureDelta(1, "EUR", 600), 1);
            harness.processElement(new ExposureMessages.ExposureDelta(1, "EUR", 60), 2);
            harness.processElement(new ExposureMessages.ExposureDelta(1, "EUR", -10), 3);

            assertThat(totals(harness).get(2).total()).isEqualTo(650.0);
        }
    }

    @Test
    @DisplayName("each currency in a fund is counted on its own")
    void eachCurrencyIsSeparate() throws Exception {
        try (var harness = started()) {
            harness.processElement(new ExposureMessages.ExposureDelta(1, "EUR", 600), 1);
            harness.processElement(new ExposureMessages.ExposureDelta(1, "GBP", 250), 2);

            assertThat(totals(harness))
                    .containsExactly(
                            new ExposureMessages.RunningTotal(1, "EUR", 600.0),
                            new ExposureMessages.RunningTotal(1, "GBP", 250.0));
        }
    }

    @Test
    @DisplayName("two funds holding the same currency do not share a total")
    void twoFundsDoNotShareATotal() throws Exception {
        try (var harness = started()) {
            harness.processElement(new ExposureMessages.ExposureDelta(1, "EUR", 600), 1);
            harness.processElement(new ExposureMessages.ExposureDelta(2, "EUR", 900), 2);

            assertThat(totals(harness))
                    .containsExactly(
                            new ExposureMessages.RunningTotal(1, "EUR", 600.0),
                            new ExposureMessages.RunningTotal(2, "EUR", 900.0));
        }
    }

    @Test
    @DisplayName("a total survives a restart, so nothing is added twice or lost")
    void aTotalSurvivesARestart() throws Exception {
        org.apache.flink.runtime.checkpoint.OperatorSubtaskState saved;

        try (var before = started()) {
            before.processElement(new ExposureMessages.ExposureDelta(1, "EUR", 600), 1);
            saved = before.snapshot(1, 1);
        }

        var after = build();
        after.initializeState(saved);
        after.open();

        try (after) {
            after.processElement(new ExposureMessages.ExposureDelta(1, "EUR", 60), 2);

            assertThat(totals(after)).containsExactly(new ExposureMessages.RunningTotal(1, "EUR", 660.0));
        }
    }

    @Test
    @DisplayName("the message on the wire carries the fund, the currency and the total")
    void theMessageCarriesWhatMatters() {
        String message = new ExposureMessages.RunningTotal(1, "EUR", 660.12345).asMessage();

        assertThat(message).contains("\"fundId\":1").contains("\"currency\":\"EUR\"");
        assertThat(message).contains("\"exposure\":660.1235");
    }

    private KeyedOneInputStreamOperatorTestHarness<
                    String, ExposureMessages.ExposureDelta, ExposureMessages.RunningTotal>
            started() throws Exception {

        var harness = build();
        harness.open();
        return harness;
    }

    private KeyedOneInputStreamOperatorTestHarness<
                    String, ExposureMessages.ExposureDelta, ExposureMessages.RunningTotal>
            build() throws Exception {

        return new KeyedOneInputStreamOperatorTestHarness<>(
                new KeyedProcessOperator<>(new RunningTotalFunction()),
                (KeySelector<ExposureMessages.ExposureDelta, String>) ExposureMessages.ExposureDelta::key,
                Types.STRING);
    }

    private List<ExposureMessages.RunningTotal> totals(
            KeyedOneInputStreamOperatorTestHarness<
                            String, ExposureMessages.ExposureDelta, ExposureMessages.RunningTotal>
                    harness) {

        return harness.extractOutputValues();
    }
}
