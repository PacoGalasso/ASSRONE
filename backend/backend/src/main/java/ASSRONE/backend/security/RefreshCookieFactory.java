package ASSRONE.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Builds the HttpOnly cookie carrying the refresh token, and its matching
 * "clear" counterpart. Centralized here so every place that issues or
 * removes the cookie (login, refresh, logout, and the invalid-refresh-token
 * error handler) uses byte-for-byte identical attributes — a mismatch on
 * Path/Domain/SameSite would silently create a second cookie instead of
 * overwriting/deleting the original one.
 *
 * Name, secure flag and SameSite are all externally configurable (see
 * application.properties) so a real deployment can flip {@code secure} to
 * true for production HTTPS without a code change — never hard-code
 * Secure=false as a "production" value.
 */
@Component
public class RefreshCookieFactory {

    private final String cookieName;
    private final boolean secure;
    private final String sameSite;
    private final String path;

    public RefreshCookieFactory(
            @Value("${app.security.refresh-cookie.name:refresh_token}") String cookieName,
            @Value("${app.security.refresh-cookie.secure:false}") boolean secure,
            @Value("${app.security.refresh-cookie.same-site:Lax}") String sameSite,
            @Value("${app.security.refresh-cookie.path:/auth}") String path) {
        this.cookieName = cookieName;
        this.secure = secure;
        this.sameSite = sameSite;
        this.path = path;
    }

    public String getCookieName() {
        return cookieName;
    }

    /**
     * Max-Age mirrors the refresh token's own real expiration (passed in by
     * the caller, derived from the JWT's own exp claim) rather than a
     * separately hard-coded duration, so the two can never drift apart.
     */
    public ResponseCookie issue(String refreshToken, Duration maxAge) {
        return baseBuilder(refreshToken)
                .maxAge(maxAge)
                .build();
    }

    /**
     * A Set-Cookie with the same name/path/domain and Max-Age=0 is how a
     * cookie is actually deleted client-side — the browser matches on those
     * attributes, not on value, so they must stay identical to issue()'s.
     */
    public ResponseCookie clear() {
        return baseBuilder("")
                .maxAge(0)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseBuilder(String value) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path);
    }
}
