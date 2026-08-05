package ASSRONE.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

/**
 * Decides whether a request's {@code Origin} (or, absent that, {@code Referer})
 * is one of the exact origins allowed to make credentialed requests. Reads
 * {@code app.security.cors.allowed-origins} directly via {@code @Value} — the
 * same property, with the same default, that both
 * {@code SecurityConfig#corsConfigurationSource} and {@code CorsProperties}
 * (the fail-fast startup validator for this property) read — rather than
 * depending on the {@code CorsProperties} bean itself, so this component stays
 * usable in {@code @WebMvcTest} slices that don't enable configuration-
 * properties binding (see RefreshCookieFactory for the identical rationale).
 *
 * Comparison is structural (scheme + host + port via {@link URI}), never a
 * {@code startsWith}/{@code contains}/regex match: "http://assrone.ch.evil.com"
 * or "http://assrone.ch:4200@evil.com" must never be mistaken for
 * "http://assrone.ch". "Origin: null" (sandboxed iframes, some redirects) and
 * any value that fails strict URI parsing are rejected, not treated as absent.
 * {@code Origin} takes precedence whenever present; {@code Referer} is only
 * ever consulted when {@code Origin} is entirely missing from the request, and
 * both missing at once is rejected — there is no silent "allowed by default"
 * fallback for a cookie-authenticated endpoint.
 */
@Component
public class OriginValidator {

    private final List<URI> allowedOrigins;

    public OriginValidator(
            @Value("${app.security.cors.allowed-origins:http://localhost:4200}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins.stream()
                .map(OriginValidator::parseStrict)
                .filter(uri -> uri != null && uri.getHost() != null)
                .toList();
    }

    public boolean isAllowed(String originHeader, String refererHeader) {
        if (originHeader != null) {
            return matchesAllowedOrigin(parseStrict(originHeader));
        }
        if (refererHeader != null) {
            return matchesAllowedOrigin(originOf(parseStrict(refererHeader)));
        }
        return false;
    }

    private boolean matchesAllowedOrigin(URI candidate) {
        if (candidate == null || candidate.getHost() == null || candidate.getScheme() == null) {
            return false;
        }
        return allowedOrigins.stream().anyMatch(allowed -> sameOrigin(allowed, candidate));
    }

    private static boolean sameOrigin(URI a, URI b) {
        return a.getScheme().equalsIgnoreCase(b.getScheme())
                && a.getHost().equalsIgnoreCase(b.getHost())
                && effectivePort(a) == effectivePort(b);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        return switch (scheme) {
            case "https" -> 443;
            case "http" -> 80;
            default -> -1;
        };
    }

    // Referer carries a full URL (with path/query); only its scheme+host+port
    // — its own "origin" — is ever compared against the allow-list.
    private static URI originOf(URI referer) {
        if (referer == null || referer.getScheme() == null || referer.getHost() == null) {
            return null;
        }
        try {
            return new URI(referer.getScheme(), null, referer.getHost(), referer.getPort(), null, null, null);
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    private static URI parseStrict(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(value);
            return uri.isAbsolute() ? uri : null;
        } catch (URISyntaxException ex) {
            return null;
        }
    }
}
