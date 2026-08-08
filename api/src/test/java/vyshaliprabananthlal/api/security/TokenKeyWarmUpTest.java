package vyshaliprabananthlal.api.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class WarmUpTheTokenKeysTest {

  @Test
  @DisplayName("startup asks the decoder once, which is what fetches the keys")
  void startupFetchesTheKeys() {
    JwtDecoder decoder = mock(JwtDecoder.class);
    when(decoder.decode(anyString())).thenThrow(new BadJwtException("as expected"));

    new TokenKeyWarmUp(decoder).run(noArguments());

    verify(decoder).decode(anyString());
  }

  @Test
  @DisplayName("the token we warm up with is rejected, and that does not stop the service")
  void aRejectedWarmUpTokenDoesNotStopTheService() {
    JwtDecoder decoder = mock(JwtDecoder.class);
    when(decoder.decode(anyString())).thenThrow(new BadJwtException("not a token"));

    assertThatCode(() -> new TokenKeyWarmUp(decoder).run(noArguments())).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("a provider that is not answering yet does not stop the service starting")
  void anUnreachableProviderDoesNotStopStartup() {
    JwtDecoder decoder = mock(JwtDecoder.class);
    when(decoder.decode(anyString()))
        .thenThrow(new JwtException("could not reach the token provider"));

    assertThatCode(() -> new TokenKeyWarmUp(decoder).run(noArguments())).doesNotThrowAnyException();
  }

  private static DefaultApplicationArguments noArguments() {
    return new DefaultApplicationArguments();
  }
}
