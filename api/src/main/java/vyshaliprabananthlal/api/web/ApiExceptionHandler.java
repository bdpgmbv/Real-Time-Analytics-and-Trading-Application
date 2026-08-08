package vyshaliprabananthlal.api.web;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vyshaliprabananthlal.api.who.Entitlements;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(Entitlements.NotAllowed.class)
    public ResponseEntity<Map<String, String>> refused(Entitlements.NotAllowed why) {
        LOG.warn("refused: {}", why.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", why.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> notThere(IllegalArgumentException why) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", why.getMessage()));
    }
}
