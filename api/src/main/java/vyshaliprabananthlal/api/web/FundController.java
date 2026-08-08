package vyshaliprabananthlal.api.web;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vyshaliprabananthlal.api.security.CallerIdentity;
import vyshaliprabananthlal.api.tenant.ClientScope;
import vyshaliprabananthlal.api.tenant.WhichClient;
import vyshaliprabananthlal.api.who.Entitlements;
import vyshaliprabananthlal.calculate.exposure.ExposureCalculator;
import vyshaliprabananthlal.calculate.exposure.FundExposure;

@RestController
@RequestMapping("/api/funds")
public class FundController {

    private final Entitlements entitlements;
    private final ExposureCalculator calculator;
    private final CallerIdentity whoIsAsking;
    private final WhichClient whichClient;
    private final ClientScope clientScope;

    public FundController(
            Entitlements entitlements,
            ExposureCalculator calculator,
            CallerIdentity whoIsAsking,
            WhichClient whichClient,
            ClientScope clientScope) {

        this.entitlements = entitlements;
        this.calculator = calculator;
        this.whoIsAsking = whoIsAsking;
        this.whichClient = whichClient;
        this.clientScope = clientScope;
    }

    @GetMapping
    public List<Entitlements.VisibleFund> visibleFunds(Authentication token) {
        String userId = whoIsAsking.userId(token);

        return clientScope.reading(whichClient.forUser(userId), () -> entitlements.fundsVisibleTo(userId));
    }

    @GetMapping("/{fundId}/exposure")
    public FundExposure exposureOf(
            @PathVariable int fundId,
            @RequestParam(name = "account", required = false) List<Integer> accounts,
            Authentication token) {

        String userId = whoIsAsking.userId(token);

        return clientScope.reading(whichClient.forUser(userId), () -> {
            entitlements.requireVisible(userId, fundId);

            if (accounts == null || accounts.isEmpty()) {
                return calculator.forWholeFund(fundId);
            }

            List<Integer> allowed = entitlements.filterVisible(userId, fundId, accounts);
            return calculator.forAccounts(fundId, allowed);
        });
    }
}
