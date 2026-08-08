package vyshaliprabananthlal.api.web;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vyshaliprabananthlal.api.who.Entitlements;

/**
 * Turns the two refusals a caller can cause into answers they can act on.
 *
 * <p>A refusal says only that it was refused. It never says whether the thing exists, so
 * probing for another client's funds learns nothing either way.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(Entitlements.NotAllowed.class)
    public ResponseEntity<Map<String, String>> refused(Entitlements.NotAllowed refusal) {
        LOG.warn("refused: {}", refusal.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", refusal.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> notFound(IllegalArgumentException missing) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", missing.getMessage()));
    }
}
