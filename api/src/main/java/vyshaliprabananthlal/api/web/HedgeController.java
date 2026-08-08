package vyshaliprabananthlal.api.web;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vyshaliprabananthlal.api.security.CallerIdentity;
import vyshaliprabananthlal.api.who.Entitlements;
import vyshaliprabananthlal.calculate.exposure.ExposureCalculator;
import vyshaliprabananthlal.calculate.hedge.HedgeAdviser;
import vyshaliprabananthlal.calculate.hedge.HedgeBook;
import vyshaliprabananthlal.calculate.hedge.Recommendation;

@RestController
@RequestMapping("/api/funds/{fundId}/hedges")
public class HedgeController {

  private final Entitlements entitlements;
  private final ExposureCalculator calculator;
  private final HedgeAdviser adviser;
  private final HedgeBook book;
  private final CallerIdentity whoIsAsking;

  public HedgeController(
      Entitlements entitlements,
      ExposureCalculator calculator,
      HedgeAdviser adviser,
      HedgeBook book,
      CallerIdentity whoIsAsking) {

    this.entitlements = entitlements;
    this.calculator = calculator;
    this.adviser = adviser;
    this.book = book;
    this.whoIsAsking = whoIsAsking;
  }

  @GetMapping("/suggested")
  public List<Recommendation> suggestions(@PathVariable int fundId, Authentication token) {
    entitlements.requireVisible(whoIsAsking.userId(token), fundId);

    return adviser.recommendFor(calculator.forWholeFund(fundId));
  }

  @PostMapping
  public SentHedges send(
      @PathVariable int fundId, @RequestBody WhatTheClientChose chose, Authentication token) {

    String userId = whoIsAsking.userId(token);
    entitlements.requireTradePermission(userId, fundId);

    List<Recommendation> advice = adviser.recommendFor(calculator.forWholeFund(fundId));
    List<Long> sent = book.submit(fundId, advice, chose.amounts(), userId);

    return new SentHedges(sent, sent.size() + " hedges sent to the market");
  }

  public record WhatTheClientChose(List<Double> amounts) {}

  public record SentHedges(List<Long> hedgeIds, String message) {}
}
