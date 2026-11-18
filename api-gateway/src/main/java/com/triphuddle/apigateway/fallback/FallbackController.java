package com.triphuddle.apigateway.fallback;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.time.Instant;
import java.util.Map;

/**
 * Where a route's circuit breaker sends a request when the service behind it is down or too slow.
 */
@RestController
public class FallbackController {

    /**
     * Answer that the service is unavailable, so clients retry later rather than take it for a missing resource.
     *
     * @param exchange the request that could not be served
     * @return 503 with the error in the services' shape
     */
    @RequestMapping("/fallback")
    public ResponseEntity<Map<String, Object>> fallback(ServerWebExchange exchange) {
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "path", exchange.getRequest().getPath().value(),
                "message", "The service is unavailable; try again shortly"));
    }
}
