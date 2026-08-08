package vyshaliprabananthlal.api.web;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vyshaliprabananthlal.api.security.CallerIdentity;
import vyshaliprabananthlal.api.security.SecurityConfig;
import vyshaliprabananthlal.api.who.Entitlements;
import vyshaliprabananthlal.calculate.exposure.ExposureCalculator;
import vyshaliprabananthlal.calculate.exposure.FundExposure;
import vyshaliprabananthlal.calculate.hedge.HedgeAdviser;
import vyshaliprabananthlal.calculate.hedge.HedgeBook;

@WebMvcTest(
        controllers = {FundController.class, HedgeController.class},
        properties = {
            "spring.datasource.password=not-a-real-password-for-tests",
            "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/not-used",
            "rtat.oidc.user-claim=sub"
        })
@Import({SecurityConfig.class, CallerIdentity.class, ApiExceptionHandler.class})
class WhoCanCallWhatTest {

    @Autowired
    private MockMvc http;

    @MockitoBean
    private org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    @MockitoBean
    private org.springframework.jdbc.core.JdbcTemplate database;

    @MockitoBean
    private io.micrometer.core.instrument.MeterRegistry meters;

    @MockitoBean
    private vyshaliprabananthlal.api.tenant.ClientLookup whichClient;

    @MockitoBean
    private vyshaliprabananthlal.api.tenant.ClientScope clientScope;

    @MockitoBean
    private Entitlements entitlements;

    @MockitoBean
    private ExposureCalculator calculator;

    @MockitoBean
    private HedgeAdviser adviser;

    @MockitoBean
    private HedgeBook book;

    @org.junit.jupiter.api.BeforeEach
    void theClientScopeRunsTheWork() {
        when(clientScope.reading(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> ((java.util.function.Supplier<?>) call.getArgument(1)).get());
    }

    @Test
    @DisplayName("no token at all is refused before anything is looked up")
    void noTokenIsRefused() throws Exception {
        http.perform(get("/api/funds")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a token that is not signed by our provider is refused")
    void aRubbishTokenIsRefused() throws Exception {
        when(jwtDecoder.decode(anyString()))
                .thenThrow(new org.springframework.security.oauth2.jwt.BadJwtException("bad signature"));

        http.perform(get("/api/funds").header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized());

        org.mockito.Mockito.verify(entitlements, org.mockito.Mockito.never()).fundsVisibleTo(anyString());
    }

    @Test
    @DisplayName("a valid token gets only the funds that user may see")
    void aValidTokenGetsTheirOwnFunds() throws Exception {
        when(entitlements.fundsVisibleTo("adriatic-reader"))
                .thenReturn(List.of(new Entitlements.VisibleFund(10, "Adriatic Growth", "USD", false)));

        http.perform(get("/api/funds").with(jwt().jwt(it -> it.subject("adriatic-reader"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fundId").value(10))
                .andExpect(jsonPath("$[0].canSendTrades").value(false));
    }

    @Test
    @DisplayName("asking for another company's fund gets 403, and the exposure is never calculated")
    void anotherCompanysFundIsForbidden() throws Exception {
        doThrow(new Entitlements.NotAllowed("you cannot see fund 20"))
                .when(entitlements)
                .requireVisible(anyString(), anyInt());

        http.perform(get("/api/funds/20/exposure").with(jwt().jwt(it -> it.subject("adriatic-reader"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("you cannot see fund 20"));

        org.mockito.Mockito.verify(calculator, org.mockito.Mockito.never()).forWholeFund(anyInt());
    }

    @Test
    @DisplayName("a reader may not send hedges even for a fund they can see")
    void aReaderMayNotSendHedges() throws Exception {
        doThrow(new Entitlements.NotAllowed("you may look at fund 10 but not send trades for it"))
                .when(entitlements)
                .requireTradePermission(anyString(), anyInt());

        http.perform(post("/api/funds/10/hedges")
                        .with(jwt().jwt(it -> it.subject("adriatic-reader")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amounts\":[-5000000]}"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("not send trades")));

        org.mockito.Mockito.verify(book, org.mockito.Mockito.never())
                .submit(anyInt(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    @DisplayName("the user the hedge is recorded against comes from the token, not the request body")
    void theUserComesFromTheTokenNotTheBody() throws Exception {
        when(calculator.forWholeFund(10)).thenReturn(new FundExposure(10, "USD", List.of(), 0));
        when(adviser.recommendFor(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(book.submit(anyInt(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyString()))
                .thenReturn(List.of(1L));

        http.perform(post("/api/funds/10/hedges")
                        .with(jwt().jwt(it -> it.subject("adriatic-trader")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amounts\":[]}"))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(book)
                .submit(
                        org.mockito.ArgumentMatchers.eq(10),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq("adriatic-trader"));
    }

    @Test
    @DisplayName("accounts asked for are narrowed to the ones that user may see")
    void accountsAreNarrowed() throws Exception {
        when(entitlements.filterVisible("adriatic-reader", 10, List.of(100, 200)))
                .thenReturn(List.of(100));
        when(calculator.forAccounts(10, List.of(100))).thenReturn(new FundExposure(10, "USD", List.of(), 1));

        http.perform(get("/api/funds/10/exposure?account=100&account=200")
                        .with(jwt().jwt(it -> it.subject("adriatic-reader"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountCount").value(1));

        org.mockito.Mockito.verify(calculator).forAccounts(10, List.of(100));
    }

    @Test
    @DisplayName("health is reachable without a token so an operator can check it")
    void healthNeedsNoToken() throws Exception {
        http.perform(get("/actuator/health/readiness")).andExpect(status().is(org.hamcrest.Matchers.not(401)));
    }
}
