package ASSRONE.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Deliberately not Spring Boot Actuator, which this project does not depend
 * on: a reverse proxy fronting this API (see deployment/nginx) needs
 * something unauthenticated to poll before routing traffic, but nothing more
 * — no database/disk/bean details, no version, no environment. Actuator's
 * management endpoints would need to be individually locked down to reach
 * this same minimal shape; this is that shape directly, with no extra
 * surface to lock down in the first place.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
