package ASSRONE.backend.ratelimit;

import org.springframework.http.HttpMethod;

public record RateLimitedRoute(HttpMethod method, String pathPattern, RateLimitCategory category) {
}
