package ASSRONE.backend.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gives every request a stable correlation ID, from the very first filter in
 * the chain to the response actually being written — including error
 * responses (401/403/429/500 alike), since the response header is set before
 * {@code filterChain.doFilter} runs, not after.
 *
 * A client-supplied {@code X-Correlation-ID} is honored only if it looks like
 * a real identifier (bounded length, restricted character set) — anything
 * else is silently replaced with a fresh UUID rather than rejected outright,
 * since an invalid incoming value is far more likely to be a misbehaving
 * client than an attack, and the request should proceed either way. A token
 * value must never end up here: this header identifies a request for log
 * correlation, not a caller.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    private static final int MAX_LENGTH = 100;
    private static final Pattern SAFE_VALUE = Pattern.compile("^[A-Za-z0-9-]{1,100}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = resolve(request.getHeader(HEADER_NAME));

        response.setHeader(HEADER_NAME, correlationId);
        MDC.put(MDC_KEY, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Tomcat threads are pooled and reused across unrelated requests: an
            // MDC value left behind here would silently leak onto the next
            // request that happens to land on the same thread.
            MDC.remove(MDC_KEY);
        }
    }

    private static String resolve(String incoming) {
        if (incoming != null && incoming.length() <= MAX_LENGTH && SAFE_VALUE.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }
}
