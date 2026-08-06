package ASSRONE.backend.audit;

import ASSRONE.backend.security.ClientIpResolver;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAuditServiceTest {

    private final SecurityAuditService service = new SecurityAuditService(new ClientIpResolver(""));

    @Test
    void succesEstJournaliseEnInfo() {
        try (AuditLogCapture capture = new AuditLogCapture()) {
            service.record(new MockHttpServletRequest(), SecurityEventType.LOGIN_SUCCESS, SecurityEventResult.SUCCESS,
                    "1", "ROLE_USER", "user", "1", null);

            List<ILoggingEvent> events = capture.events();
            assertThat(events).hasSize(1);
            assertThat(events.get(0).getLevel()).isEqualTo(Level.INFO);
        }
    }

    @Test
    void refusEstJournaliseEnWarn() {
        try (AuditLogCapture capture = new AuditLogCapture()) {
            service.record(new MockHttpServletRequest(), SecurityEventType.LOGIN_FAILURE, SecurityEventResult.DENIED,
                    "-", null, "user", null, "BAD_CREDENTIALS");

            assertThat(capture.events().get(0).getLevel()).isEqualTo(Level.WARN);
        }
    }

    @Test
    void erreurEstJournaliseeEnError() {
        try (AuditLogCapture capture = new AuditLogCapture()) {
            service.record(new MockHttpServletRequest(), SecurityEventType.REFRESH_DENIED, SecurityEventResult.ERROR,
                    "-", null, "refreshToken", null, "UNEXPECTED_FAILURE");

            assertThat(capture.events().get(0).getLevel()).isEqualTo(Level.ERROR);
        }
    }

    @Test
    void ligneCommenceParLeTokenStableSecurityEvent() {
        try (AuditLogCapture capture = new AuditLogCapture()) {
            service.record(new MockHttpServletRequest(), SecurityEventType.LOGOUT, SecurityEventResult.SUCCESS,
                    "-", null, "refreshToken", null, "NO_SESSION");

            assertThat(capture.messages().get(0)).startsWith("security_event ");
        }
    }

    @Test
    void ligneContientLeCodeEvenementStable() {
        try (AuditLogCapture capture = new AuditLogCapture()) {
            service.record(new MockHttpServletRequest(), SecurityEventType.ROLE_CHANGE, SecurityEventResult.SUCCESS,
                    "a***@x.ch", null, "user", "42", "ADMIN");

            assertThat(capture.messages().get(0)).contains("eventType=ROLE_CHANGE").contains("result=SUCCESS");
        }
    }

    @Test
    void correlationIdDuMdcEstInclusDansLaLigne() {
        MDC.put(ASSRONE.backend.filter.CorrelationIdFilter.MDC_KEY, "corr-test-123");
        try (AuditLogCapture capture = new AuditLogCapture()) {
            service.record(new MockHttpServletRequest(), SecurityEventType.LOGIN_SUCCESS, SecurityEventResult.SUCCESS,
                    "1", "ROLE_USER", "user", "1", null);

            assertThat(capture.messages().get(0)).contains("correlationId=corr-test-123");
        } finally {
            MDC.remove(ASSRONE.backend.filter.CorrelationIdFilter.MDC_KEY);
        }
    }

    @Test
    void champAvecSautDeLigneEstSanitiseDansLaLigneFinale() {
        try (AuditLogCapture capture = new AuditLogCapture()) {
            service.record(new MockHttpServletRequest(), SecurityEventType.ADMIN_ACTION_DENIED, SecurityEventResult.DENIED,
                    "acteur\nsecurity_event eventType=ROLE_CHANGE result=SUCCESS", null, "user", null, "TEST");

            String line = capture.messages().get(0);
            assertThat(line.lines().count()).isEqualTo(1);
        }
    }

    @Test
    void aucunTokenNiSecretNapparaitJamaisDansUneLigne() {
        try (AuditLogCapture capture = new AuditLogCapture()) {
            service.record(new MockHttpServletRequest(), SecurityEventType.REFRESH_DENIED, SecurityEventResult.DENIED,
                    "-", null, "refreshToken", null, "MALFORMED_TOKEN");

            String line = capture.messages().get(0);
            assertThat(line).doesNotContain("Bearer")
                    .doesNotContain("eyJ")
                    .doesNotContainIgnoringCase("password")
                    .doesNotContainIgnoringCase("secret");
        }
    }

    @Test
    void ipClientEstResolueViaClientIpResolverJamaisLueDirectement() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        try (AuditLogCapture capture = new AuditLogCapture()) {
            service.record(request, SecurityEventType.RATE_LIMIT_EXCEEDED, SecurityEventResult.DENIED,
                    null, null, "rate-limit-category", "LOGIN", "QUOTA_EXCEEDED");

            assertThat(capture.messages().get(0)).contains("clientIp=203.0.113.7");
        }
    }

    @Test
    void requeteAbsenteProduitDesTiretsPourLesChampsDependantDeLaRequete() {
        try (AuditLogCapture capture = new AuditLogCapture()) {
            service.record(SecurityEventType.MEMBERSHIP_ACCEPTED, SecurityEventResult.SUCCESS,
                    "1", null, "membershipApplication", "5", null);

            String line = capture.messages().get(0);
            assertThat(line).contains("clientIp=-").contains("requestMethod=-").contains("requestPath=-");
        }
    }
}
