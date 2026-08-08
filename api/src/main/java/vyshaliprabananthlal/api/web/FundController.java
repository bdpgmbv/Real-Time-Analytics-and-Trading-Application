package vyshaliprabananthlal.api.web;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vyshaliprabananthlal.api.security.CallerIdentity;
import vyshaliprabananthlal.api.who.Entitlements;
import vyshaliprabananthlal.api.who.VisibleFund;
import vyshaliprabananthlal.calculate.exposure.ExposureCalculator;
import vyshaliprabananthlal.calculate.exposure.FundExposure;

@RestController
@RequestMapping("/api/funds")
public class FundController {

  private final Entitlements entitlements;
  private final ExposureCalculator calculator;
  private final CallerIdentity whoIsAsking;

  public FundController(
      Entitlements entitlements, ExposureCalculator calculator, CallerIdentity whoIsAsking) {

    this.entitlements = entitlements;
    this.calculator = calculator;
    this.whoIsAsking = whoIsAsking;
  }

  @GetMapping
  public List<VisibleFund> fundsICanSee(Authentication token) {
    return entitlements.fundsVisibleTo(whoIsAsking.userId(token));
  }

  @GetMapping("/{fundId}/exposure")
  public FundExposure exposureOf(
      @PathVariable int fundId,
      @RequestParam(name = "account", required = false) List<Integer> accounts,
      Authentication token) {

    String userId = whoIsAsking.userId(token);
    entitlements.mustBeAbleToSee(userId, fundId);

    if (accounts == null || accounts.isEmpty()) {
      return calculator.forWholeFund(fundId);
    }

    List<Integer> allowed = entitlements.narrowToWhatTheyMaySee(userId, fundId, accounts);
    return calculator.forAccounts(fundId, allowed);
  }
}
