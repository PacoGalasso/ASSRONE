package ASSRONE.backend.filter;

import ASSRONE.backend.security.OriginValidator;
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
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Defense-in-depth cross-site request check for the only two endpoints that
 * authenticate a request purely from a cookie the browser attaches on its
 * own — {@code POST /auth/refresh} and {@code POST /auth/logout}. Every other
 * endpoint either requires an explicit {@code Authorization: Bearer} header
 * (never attached automatically by a browser) or doesn't touch the refresh
 * cookie at all, so this filter leaves them untouched.
 *
 * SameSite=Lax on the refresh cookie (see RefreshCookieFactory) already
 * withholds it from cross-site POSTs in any spec-compliant browser; this
 * filter is a second, independent layer that doesn't rely on that behavior
 * alone — it rejects same-site-but-cross-origin requests SameSite cannot
 * distinguish, and stays effective even if a browser, extension, or proxy
 * mishandles SameSite.
 *
 * Runs before RateLimitFilter so a forged request never consumes a
 * legitimate client's rate-limit budget. Never touches OPTIONS (CORS
 * preflight) or any method other than POST — a GET/PUT/etc. to these paths
 * falls straight through to Spring MVC's normal 405 handling.
 */
@Component
@RequiredArgsConstructor
public class AuthCookieOriginFilter extends OncePerRequestFilter {

    private static final String FORBIDDEN_BODY =
            "{\"error\":\"Origine de la requête non autorisée.\"}";
    private static final Set<String> PROTECTED_PATHS = Set.of("/auth/refresh", "/auth/logout");

    private final OriginValidator originValidator;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isProtected(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String origin = request.getHeader(HttpHeaders.ORIGIN);
        String referer = request.getHeader(HttpHeaders.REFERER);

        if (!originValidator.isAllowed(origin, referer)) {
            writeForbidden(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isProtected(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod()) && PROTECTED_PATHS.contains(request.getRequestURI());
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(FORBIDDEN_BODY);
    }
}
