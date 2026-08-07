package vyshaliprabananthlal.jobs.exposure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.streaming.api.operators.KeyedProcessOperator;
import org.apache.flink.streaming.util.KeyedOneInputStreamOperatorTestHarness;
import org.apache.flink.streaming.util.OneInputStreamOperatorTestHarness;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WhatThePriceMoveDidTest {

  private static final int AIRBUS = 100;
  private static final int SAP = 101;

  @Test
  @DisplayName("the first price seen sets the whole value, because there is nothing to compare to")
  void theFirstPriceSetsTheWholeValue() throws Exception {
    try (var harness = holding(new WhoHoldsIt(1, "EUR", 6))) {
      harness.processElement(new PriceTick(AIRBUS, 100), 1);

      assertThat(harness.extractOutputValues()).containsExactly(new ExposureDelta(1, "EUR", 600.0));
    }
  }

  @Test
  @DisplayName("a later price sends only the difference, not the whole value again")
  void aLaterPriceSendsOnlyTheDifference() throws Exception {
    try (var harness = holding(new WhoHoldsIt(1, "EUR", 6))) {
      harness.processElement(new PriceTick(AIRBUS, 100), 1);
      harness.processElement(new PriceTick(AIRBUS, 110), 2);

      assertThat(harness.extractOutputValues())
          .containsExactly(new ExposureDelta(1, "EUR", 600.0), new ExposureDelta(1, "EUR", 60.0));
    }
  }

  @Test
  @DisplayName("a price that did not move sends nothing at all")
  void anUnchangedPriceSendsNothing() throws Exception {
    try (var harness = holding(new WhoHoldsIt(1, "EUR", 6))) {
      harness.processElement(new PriceTick(AIRBUS, 100), 1);
      harness.processElement(new PriceTick(AIRBUS, 100), 2);

      assertThat(harness.extractOutputValues()).hasSize(1);
    }
  }

  @Test
  @DisplayName("a price falling sends a negative difference")
  void aFallingPriceSendsANegative() throws Exception {
    try (var harness = holding(new WhoHoldsIt(1, "EUR", 6))) {
      harness.processElement(new PriceTick(AIRBUS, 100), 1);
      harness.processElement(new PriceTick(AIRBUS, 90), 2);

      assertThat(harness.extractOutputValues().get(1).changeBy()).isEqualTo(-60.0);
    }
  }

  @Test
  @DisplayName("every fund holding that security gets its own difference")
  void everyHolderGetsItsOwn() throws Exception {
    try (var harness = holding(new WhoHoldsIt(1, "EUR", 6), new WhoHoldsIt(2, "EUR", 100))) {
      harness.processElement(new PriceTick(AIRBUS, 100), 1);
      harness.processElement(new PriceTick(AIRBUS, 110), 2);

      assertThat(harness.extractOutputValues())
          .contains(new ExposureDelta(1, "EUR", 60.0), new ExposureDelta(2, "EUR", 1000.0));
    }
  }

  @Test
  @DisplayName("a price for a security nobody holds costs nothing and sends nothing")
  void aSecurityNobodyHoldsSendsNothing() throws Exception {
    try (var harness = holding(new WhoHoldsIt(1, "EUR", 6))) {
      harness.processElement(new PriceTick(SAP, 50), 1);
      harness.processElement(new PriceTick(SAP, 60), 2);

      assertThat(harness.extractOutputValues()).isEmpty();
    }
  }

  @Test
  @DisplayName("a short holding moves the exposure the other way")
  void aShortHoldingMovesTheOtherWay() throws Exception {
    try (var harness = holding(new WhoHoldsIt(1, "EUR", -6))) {
      harness.processElement(new PriceTick(AIRBUS, 100), 1);
      harness.processElement(new PriceTick(AIRBUS, 110), 2);

      assertThat(harness.extractOutputValues().get(1).changeBy()).isEqualTo(-60.0);
    }
  }

  @Test
  @DisplayName("the last price survives a restart, so the value is not counted twice")
  void theLastPriceSurvivesARestart() throws Exception {
    org.apache.flink.runtime.checkpoint.OperatorSubtaskState saved;

    try (var before = holding(new WhoHoldsIt(1, "EUR", 6))) {
      before.processElement(new PriceTick(AIRBUS, 100), 1);
      saved = before.snapshot(1, 1);
    }

    try (var after = harnessFor(Map.of(AIRBUS, List.of(new WhoHoldsIt(1, "EUR", 6))), saved)) {
      after.processElement(new PriceTick(AIRBUS, 110), 2);

      assertThat(after.extractOutputValues()).containsExactly(new ExposureDelta(1, "EUR", 60.0));
    }
  }

  private OneInputStreamOperatorTestHarness<PriceTick, ExposureDelta> holding(WhoHoldsIt... holders)
      throws Exception {

    return startedFrom(Map.of(AIRBUS, List.of(holders)));
  }

  private OneInputStreamOperatorTestHarness<PriceTick, ExposureDelta> startedFrom(
      Map<Integer, List<WhoHoldsIt>> whoHoldsWhat) throws Exception {

    var harness = build(whoHoldsWhat);
    harness.open();
    return harness;
  }

  private OneInputStreamOperatorTestHarness<PriceTick, ExposureDelta> harnessFor(
      Map<Integer, List<WhoHoldsIt>> whoHoldsWhat,
      org.apache.flink.runtime.checkpoint.OperatorSubtaskState startFrom)
      throws Exception {

    var harness = build(whoHoldsWhat);
    harness.initializeState(startFrom);
    harness.open();
    return harness;
  }

  private KeyedOneInputStreamOperatorTestHarness<Integer, PriceTick, ExposureDelta> build(
      Map<Integer, List<WhoHoldsIt>> whoHoldsWhat) throws Exception {

    return new KeyedOneInputStreamOperatorTestHarness<>(
        new KeyedProcessOperator<>(new WhatThePriceMoveDid(whoHoldsWhat)),
        (org.apache.flink.api.java.functions.KeySelector<PriceTick, Integer>) PriceTick::productId,
        Types.INT);
  }
}
