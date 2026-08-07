package vyshaliprabananthlal.api.web;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vyshaliprabananthlal.api.security.WhoIsAsking;
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
  private final WhoIsAsking whoIsAsking;

  public HedgeController(
      Entitlements entitlements,
      ExposureCalculator calculator,
      HedgeAdviser adviser,
      HedgeBook book,
      WhoIsAsking whoIsAsking) {

    this.entitlements = entitlements;
    this.calculator = calculator;
    this.adviser = adviser;
    this.book = book;
    this.whoIsAsking = whoIsAsking;
  }

  @GetMapping("/suggested")
  public List<Recommendation> whatWeSuggest(@PathVariable int fundId, Authentication token) {
    entitlements.mustBeAbleToSee(whoIsAsking.userId(token), fundId);

    return adviser.whatToHedge(calculator.forWholeFund(fundId));
  }

  @PostMapping
  public SentHedges send(
      @PathVariable int fundId, @RequestBody WhatTheClientChose chose, Authentication token) {

    String userId = whoIsAsking.userId(token);
    entitlements.mustBeAbleToSendTradesFor(userId, fundId);

    List<Recommendation> advice = adviser.whatToHedge(calculator.forWholeFund(fundId));
    List<Long> sent = book.sendToTheMarket(fundId, advice, chose.amounts(), userId);

    return new SentHedges(sent, sent.size() + " hedges sent to the market");
  }

  public record WhatTheClientChose(List<Double> amounts) {}

  public record SentHedges(List<Long> hedgeIds, String message) {}
}
