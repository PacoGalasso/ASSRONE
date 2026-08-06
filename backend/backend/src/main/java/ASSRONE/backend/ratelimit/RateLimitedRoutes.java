package ASSRONE.backend.ratelimit;

import org.springframework.http.HttpMethod;

import java.util.List;

/**
 * Single source of truth for which routes are rate-limited and under which
 * category. Both RateLimitFilter and its tests read from this list so the
 * path-matching rules never drift into more than one place.
 */
public final class RateLimitedRoutes {

    public static final List<RateLimitedRoute> ROUTES = List.of(
            new RateLimitedRoute(HttpMethod.POST, "/auth/generateToken", RateLimitCategory.LOGIN),
            new RateLimitedRoute(HttpMethod.POST, "/auth/addNewUser", RateLimitCategory.ACCOUNT_CREATION),
            new RateLimitedRoute(HttpMethod.POST, "/api/contact", RateLimitCategory.CONTACT),
            new RateLimitedRoute(HttpMethod.POST, "/api/membership-applications", RateLimitCategory.MEMBERSHIP_APPLICATION),
            new RateLimitedRoute(HttpMethod.POST, "/api/events/*/register", RateLimitCategory.EVENT_REGISTRATION),
            new RateLimitedRoute(HttpMethod.POST, "/auth/forgot-password", RateLimitCategory.PASSWORD_RESET_REQUEST),
            new RateLimitedRoute(HttpMethod.POST, "/auth/resend-verification", RateLimitCategory.EMAIL_VERIFICATION_RESEND)
    );

    private RateLimitedRoutes() {
    }
}
