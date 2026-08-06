package ASSRONE.backend.filter;

import ASSRONE.backend.audit.AuditLogCapture;
import ASSRONE.backend.audit.SecurityAuditService;
import ASSRONE.backend.ratelimit.RateLimitCategory;
import ASSRONE.backend.ratelimit.RateLimiterService;
import ASSRONE.backend.security.ClientIpResolver;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private RateLimiterService rateLimiterService;

    @Mock
    private FilterChain filterChain;

    private RateLimitFilter filter() {
        ClientIpResolver clientIpResolver = new ClientIpResolver("");
        return new RateLimitFilter(rateLimiterService, clientIpResolver, new SecurityAuditService(clientIpResolver));
    }

    @Test
    void cheminNonConcerneEstIgnore() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/events");
        request.setRemoteAddr("10.1.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(rateLimiterService);
    }

    @Test
    void mauvaiseMethodeHttpEstIgnoree() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/contact");
        request.setRemoteAddr("10.1.0.2");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(rateLimiterService);
    }

    @Test
    void matchingCorrectPourInscriptionEvenement() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/events/42/register");
        request.setRemoteAddr("10.1.0.3");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(rateLimiterService.tryConsume("10.1.0.3", RateLimitCategory.EVENT_REGISTRATION))
                .thenReturn(ConsumptionProbe.consumed(19, 0));

        filter().doFilter(request, response, filterChain);

        verify(rateLimiterService).tryConsume("10.1.0.3", RateLimitCategory.EVENT_REGISTRATION);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void requeteAutoriseeContinueVersLeFilterChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/generateToken");
        request.setRemoteAddr("10.1.0.4");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(rateLimiterService.tryConsume(eq("10.1.0.4"), eq(RateLimitCategory.LOGIN)))
                .thenReturn(ConsumptionProbe.consumed(9, 0));

        filter().doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void requeteBloqueeNeContinuePasVersLeFilterChainEtRenvoie429() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/generateToken");
        request.setRemoteAddr("10.1.0.5");
        MockHttpServletResponse response = new MockHttpServletResponse();
        long thirtySecondsInNanos = Duration.ofSeconds(30).toNanos();
        when(rateLimiterService.tryConsume(eq("10.1.0.5"), eq(RateLimitCategory.LOGIN)))
                .thenReturn(ConsumptionProbe.rejected(0, thirtySecondsInNanos, thirtySecondsInNanos));

        filter().doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString())
                .isEqualTo("{\"error\":\"Trop de requêtes. Veuillez réessayer plus tard.\"}");
        assertThat(response.getHeader("Retry-After")).isEqualTo("30");
    }

    @Test
    void requeteBloqueeJournaliseRateLimitExceededAvecLaCategorie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/generateToken");
        request.setRemoteAddr("10.1.0.6");
        MockHttpServletResponse response = new MockHttpServletResponse();
        long thirtySecondsInNanos = Duration.ofSeconds(30).toNanos();
        when(rateLimiterService.tryConsume(eq("10.1.0.6"), eq(RateLimitCategory.LOGIN)))
                .thenReturn(ConsumptionProbe.rejected(0, thirtySecondsInNanos, thirtySecondsInNanos));

        try (AuditLogCapture capture = new AuditLogCapture()) {
            filter().doFilter(request, response, filterChain);

            String line = capture.messages().get(0);
            assertThat(line).contains("eventType=RATE_LIMIT_EXCEEDED")
                    .contains("result=DENIED")
                    .contains("targetId=LOGIN")
                    .contains("clientIp=10.1.0.6");
        }
    }
}
