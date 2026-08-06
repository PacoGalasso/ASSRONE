package ASSRONE.backend.audit;

import ASSRONE.backend.filter.CorrelationIdFilter;
import ASSRONE.backend.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The single place every security-relevant event in this application is
 * recorded — never a free-text {@code logger.info("user logged in")} at the
 * call site. Every field is explicitly named and sanitized; nothing here
 * ever accepts or serializes an arbitrary object, so a token/cookie/password
 * accidentally reaching a caller's local variable still can't leak through
 * this service even if a caller made that mistake — there is no parameter
 * shaped to accept it.
 *
 * One line per event, one event per occurrence: call this once, at the
 * layer that actually knows the outcome (a service method, not also its
 * controller and not also a filter further down the chain), to avoid the
 * same incident appearing multiple times in the logs.
 *
 * Deliberately not JSON: no JSON dependency is already present in this
 * project, and a stable {@code key=value} line is trivially parseable by a
 * future log shipper/SIEM without adding one. The literal leading token
 * {@code security_event} lets every consumer (grep, a log pipeline filter)
 * select exactly these lines out of the rest of the application's logging.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SecurityAuditService {

    private final ClientIpResolver clientIpResolver;

    /**
     * For use from a service or controller, where Spring has already bound
     * the current request to {@link RequestContextHolder} — the common case
     * for every auth/admin/content-mutation event in this application.
     */
    public void record(SecurityEventType eventType, SecurityEventResult result, String actorId, String actorRole,
                        String targetType, String targetId, String reasonCode) {
        record(currentRequest(), eventType, result, actorId, actorRole, targetType, targetId, reasonCode);
    }

    /**
     * For use from a servlet filter running ahead of {@code DispatcherServlet}
     * (e.g. {@code RateLimitFilter}, {@code AuthCookieOriginFilter}), where
     * {@link RequestContextHolder} is not yet guaranteed to be populated and
     * the filter already holds the real {@link HttpServletRequest} anyway.
     */
    public void record(HttpServletRequest request, SecurityEventType eventType, SecurityEventResult result,
                        String actorId, String actorRole, String targetType, String targetId, String reasonCode) {
        String clientIp = request != null ? clientIpResolver.resolve(request) : "-";
        String method = request != null ? request.getMethod() : "-";
        String path = request != null ? request.getRequestURI() : "-";
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);

        String line = "security_event"
                + " eventType=" + eventType
                + " result=" + result
                + " correlationId=" + LogSanitizer.sanitize(correlationId)
                + " actorId=" + LogSanitizer.sanitize(actorId)
                + " actorRole=" + LogSanitizer.sanitize(actorRole)
                + " targetType=" + LogSanitizer.sanitize(targetType)
                + " targetId=" + LogSanitizer.sanitize(targetId)
                + " clientIp=" + LogSanitizer.sanitize(clientIp)
                + " requestMethod=" + LogSanitizer.sanitize(method)
                + " requestPath=" + LogSanitizer.sanitize(path)
                + " reasonCode=" + LogSanitizer.sanitize(reasonCode);

        switch (result) {
            case ERROR -> log.error(line);
            case DENIED -> log.warn(line);
            case SUCCESS -> log.info(line);
        }
    }

    /**
     * For events where no internal actor ID is available yet (login failure,
     * refresh denial before the token is validated, ...): keeps the domain
     * for operational triage while never writing a full email address to the
     * logs. Prefer {@code String.valueOf(user.getId())} over this wherever an
     * internal ID is actually in scope.
     */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "-";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
