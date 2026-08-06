package ASSRONE.backend.filter;

import ASSRONE.backend.audit.SecurityAuditService;
import ASSRONE.backend.audit.SecurityEventResult;
import ASSRONE.backend.audit.SecurityEventType;
import ASSRONE.backend.ratelimit.RateLimitCategory;
import ASSRONE.backend.ratelimit.RateLimitedRoute;
import ASSRONE.backend.ratelimit.RateLimitedRoutes;
import ASSRONE.backend.ratelimit.RateLimiterService;
import ASSRONE.backend.security.ClientIpResolver;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Blocks requests to a fixed set of public, unauthenticated endpoints once their
 * per-IP-and-category budget is exhausted. Runs before JwtAuthFilter so a blocked
 * request never reaches authentication, the controller, or triggers any email/DB
 * write/BCrypt computation.
 *
 * Client IP is resolved via ClientIpResolver: request.getRemoteAddr() by default,
 * or the client IP from X-Forwarded-For only when the direct peer is a configured
 * trusted proxy (see app.security.trusted-proxies) — never trusted blindly.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String TOO_MANY_REQUESTS_BODY = "{\"error\":\"Trop de requêtes. Veuillez réessayer plus tard.\"}";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final RateLimiterService rateLimiterService;
    private final ClientIpResolver clientIpResolver;
    private final SecurityAuditService securityAuditService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Optional<RateLimitCategory> category = matchCategory(request);

        if (category.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        ConsumptionProbe probe = rateLimiterService.tryConsume(clientIpResolver.resolve(request), category.get());

        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        securityAuditService.record(request, SecurityEventType.RATE_LIMIT_EXCEEDED, SecurityEventResult.DENIED,
                null, null, "rate-limit-category", category.get().name(), "QUOTA_EXCEEDED");
        writeTooManyRequests(response, probe.getNanosToWaitForRefill());
    }

    private Optional<RateLimitCategory> matchCategory(HttpServletRequest request) {
        HttpMethod requestMethod = HttpMethod.valueOf(request.getMethod());
        String requestPath = requestPath(request);

        for (RateLimitedRoute route : RateLimitedRoutes.ROUTES) {
            if (route.method().equals(requestMethod) && pathMatcher.match(route.pathPattern(), requestPath)) {
                return Optional.of(route.category());
            }
        }
        return Optional.empty();
    }

    private String requestPath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();
        return (contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath))
                ? requestUri.substring(contextPath.length())
                : requestUri;
    }

    private void writeTooManyRequests(HttpServletResponse response, long nanosToWaitForRefill) throws IOException {
        long retryAfterSeconds = Math.max(1, ceilDivide(nanosToWaitForRefill, 1_000_000_000L));

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.getWriter().write(TOO_MANY_REQUESTS_BODY);
    }

    private static long ceilDivide(long numerator, long denominator) {
        return (numerator + denominator - 1) / denominator;
    }
}
