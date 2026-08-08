package vyshaliprabananthlal.api.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

@Component
public class TokenKeyWarmUp implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(TokenKeyWarmUp.class);

  private static final String A_TOKEN_THAT_CANNOT_BE_VALID = "warm.up.only";

  private final JwtDecoder decoder;

  public TokenKeyWarmUp(JwtDecoder decoder) {
    this.decoder = decoder;
  }

  @Override
  public void run(ApplicationArguments arguments) {
    long startedAt = System.nanoTime();

    try {
      decoder.decode(A_TOKEN_THAT_CANNOT_BE_VALID);
    } catch (RuntimeException expected) {
      LOG.debug("warm up finished the only way it can: {}", expected.getMessage());
    }

    long tookMilliseconds = (System.nanoTime() - startedAt) / 1_000_000L;
    LOG.info("fetched the token signing keys in {} ms", tookMilliseconds);
  }
}
