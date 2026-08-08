package vyshaliprabananthlal.gateway.limit;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

class WhoToCountAgainstTest {

    private final RateLimitKeyResolver counting = new RateLimitKeyResolver();

    @Test
    @DisplayName("a signed-in caller is counted against their own name, not their address")
    void aSignedInCallerIsCountedByName() {
        StepVerifier.create(counting.resolve(withPrincipal("user11")))
                .expectNext("user11")
                .verifyComplete();
    }

    @Test
    @DisplayName("two clients behind one address are still counted separately")
    void twoClientsBehindOneAddressAreSeparate() {
        var first = withPrincipal("user11");
        var second = withPrincipal("user21");

        StepVerifier.create(counting.resolve(first)).expectNext("user11").verifyComplete();
        StepVerifier.create(counting.resolve(second)).expectNext("user21").verifyComplete();
    }

    @Test
    @DisplayName("a caller with no token falls back to the address it came from")
    void noTokenFallsBackToTheAddress() {
        StepVerifier.create(counting.resolve(anExchangeFrom("203.0.113.7")))
                .expectNext("203.0.113.7")
                .verifyComplete();
    }

    @Test
    @DisplayName("a caller with no address at all is still counted, under one bucket")
    void noAddressIsStillCounted() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/funds"));

        StepVerifier.create(counting.resolve(exchange))
                .expectNext(RateLimitKeyResolver.NOBODY_IN_PARTICULAR)
                .verifyComplete();
    }

    @Test
    @DisplayName("a token with a blank name does not create an empty bucket key")
    void aBlankNameDoesNotBecomeAnEmptyKey() {
        Principal blank = () -> "   ";

        assertThat(RateLimitKeyResolver.nameFrom(blank)).isEqualTo(RateLimitKeyResolver.NOBODY_IN_PARTICULAR);
    }

    @Test
    @DisplayName("a token with a real name is used as it stands")
    void aRealNameIsUsedAsItStands() {
        Principal named = () -> "user11";

        assertThat(RateLimitKeyResolver.nameFrom(named)).isEqualTo("user11");
    }

    private MockServerWebExchange anExchangeFrom(String address) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/funds").remoteAddress(new java.net.InetSocketAddress(address, 51234)));
    }

    private MockServerWebExchange withPrincipal(String name) {
        var request = MockServerHttpRequest.get("/api/funds")
                .remoteAddress(new java.net.InetSocketAddress("203.0.113.7", 51234))
                .build();

        Principal whoTheyAre = () -> name;

        return MockServerWebExchange.builder(request).principal(whoTheyAre).build();
    }
}
