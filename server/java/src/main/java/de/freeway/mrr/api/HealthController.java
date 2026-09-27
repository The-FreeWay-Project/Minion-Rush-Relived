package de.freeway.mrr.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness endpoint — same payload as the Python MRR server.
 */
@RestController
public class HealthController {

    public record HealthResponse(String status, String service) {
    }

    @GetMapping("/api/v1/health")
    public HealthResponse health() {
        return new HealthResponse("ok", "mrr-server");
    }
}
