package ASSRONE.backend.service;

import ASSRONE.backend.audit.AuditLogCapture;
import ASSRONE.backend.audit.SecurityAuditService;
import ASSRONE.backend.config.SessionProperties;
import ASSRONE.backend.exception.InvalidSessionIdException;
import ASSRONE.backend.exception.SessionNotFoundException;
import ASSRONE.backend.model.UserSession;
import ASSRONE.backend.repository.RefreshTokenRepository;
import ASSRONE.backend.repository.UserSessionRepository;
import ASSRONE.backend.security.ClientIpResolver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SessionServiceTest {

    private static final Instant NOW_INSTANT = Instant.parse("2026-08-05T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW_INSTANT, ZoneOffset.UTC);

    private UserSessionRepository userSessionRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private EntityManager entityManager;
    private SessionService service;

    @BeforeEach
    void setUp() {
        userSessionRepository = mock(UserSessionRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        entityManager = mock(EntityManager.class);
        Query lockQuery = mock(Query.class);
        when(entityManager.createNativeQuery(org.mockito.ArgumentMatchers.anyString())).thenReturn(lockQuery);
        when(lockQuery.setParameter(org.mockito.ArgumentMatchers.anyString(), any())).thenReturn(lockQuery);

        SessionProperties.Cleanup cleanup = new SessionProperties.Cleanup(true, "0 0 3 * * *", Duration.ofDays(7));
        SessionProperties properties = new SessionProperties(5, cleanup);

        service = new SessionService(userSessionRepository, refreshTokenRepository,
                new SecurityAuditService(new ClientIpResolver("")), properties, entityManager, FIXED_CLOCK);
    }

    private static UserSession activeSession(Long id, String publicId, LocalDateTime lastUsedAt) {
        return UserSession.builder()
                .id(id)
                .publicId(publicId)
                .userId(1L)
                .createdAt(LocalDateTime.now(FIXED_CLOCK).minusDays(1))
                .lastUsedAt(lastUsedAt)
                .expiresAt(LocalDateTime.now(FIXED_CLOCK).plusDays(6))
                .build();
    }

    // ===== createSession : limite =====

    @Test
    void createSessionSousLaLimiteNeRevoqueRien() {
        when(userSessionRepository.countByUserIdAndRevokedAtIsNullAndExpiresAtAfter(eq(1L), any())).thenReturn(2L);
        when(userSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createSession(1L, LocalDateTime.now(FIXED_CLOCK).plusDays(7), "1.2.3.4", "UA");

        verify(userSessionRepository, never()).revokeById(anyLong(), any(), any());
        verify(userSessionRepository).save(any(UserSession.class));
    }

    @Test
    void createSessionAuDelaDeLaLimiteRevoqueLaPlusAncienneSession() {
        when(userSessionRepository.countByUserIdAndRevokedAtIsNullAndExpiresAtAfter(eq(1L), any())).thenReturn(5L);
        UserSession oldest = activeSession(10L, "oldest-public-id", LocalDateTime.now(FIXED_CLOCK).minusDays(3));
        when(userSessionRepository.findFirstByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastUsedAtAsc(eq(1L), any()))
                .thenReturn(Optional.of(oldest));
        when(userSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        try (AuditLogCapture capture = new AuditLogCapture()) {
            service.createSession(1L, LocalDateTime.now(FIXED_CLOCK).plusDays(7), "1.2.3.4", "UA");

            assertThat(capture.messages()).anySatisfy(line ->
                    assertThat(line).contains("eventType=SESSION_LIMIT_ENFORCED")
                            .contains("result=SUCCESS")
                            .contains("targetId=oldest-public-id"));
        }
        verify(userSessionRepository).revokeById(eq(10L), any(), eq("SESSION_LIMIT_ENFORCED"));
        verify(refreshTokenRepository).revokeAllForSession(eq(10L), any());
    }

    @Test
    void createSessionAcquiertLeVerrouAvantDeCompterLesSessionsActives() {
        when(userSessionRepository.countByUserIdAndRevokedAtIsNullAndExpiresAtAfter(eq(1L), any())).thenReturn(0L);
        when(userSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createSession(1L, LocalDateTime.now(FIXED_CLOCK).plusDays(7), "1.2.3.4", "UA");

        verify(entityManager).createNativeQuery(org.mockito.ArgumentMatchers.contains("pg_advisory_xact_lock"));
    }

    @Test
    void createSessionSanitiseEtTronqueUnUserAgentTropLong() {
        when(userSessionRepository.countByUserIdAndRevokedAtIsNullAndExpiresAtAfter(eq(1L), any())).thenReturn(0L);
        var captor = org.mockito.ArgumentCaptor.forClass(UserSession.class);
        when(userSessionRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        String longUserAgent = "A".repeat(400) + "control-char";
        service.createSession(1L, LocalDateTime.now(FIXED_CLOCK).plusDays(7), "1.2.3.4", longUserAgent);

        assertThat(captor.getValue().getUserAgentLabel()).hasSize(255).doesNotContain("");
    }

    @Test
    void createSessionAvecUserAgentNullNeLeveRien() {
        when(userSessionRepository.countByUserIdAndRevokedAtIsNullAndExpiresAtAfter(eq(1L), any())).thenReturn(0L);
        when(userSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserSession saved = service.createSession(1L, LocalDateTime.now(FIXED_CLOCK).plusDays(7), "1.2.3.4", null);

        assertThat(saved.getUserAgentLabel()).isNull();
    }

    // ===== listActiveSessions =====

    @Test
    void listActiveSessionsNeRetourneQueLesSessionsDeLUtilisateurDemande() {
        service.listActiveSessions(1L);

        verify(userSessionRepository).findAllByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastUsedAtDesc(eq(1L), any());
    }

    // ===== isSessionRevoked =====

    @Test
    void isSessionRevokedRetourneVraiPourUneSessionAbsente() {
        when(userSessionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(service.isSessionRevoked(99L)).isTrue();
    }

    @Test
    void isSessionRevokedRetourneFauxPourUneSessionActive() {
        when(userSessionRepository.findById(99L)).thenReturn(Optional.of(activeSession(99L, "pid", LocalDateTime.now(FIXED_CLOCK))));

        assertThat(service.isSessionRevoked(99L)).isFalse();
    }

    // ===== revokeOwnSession =====

    @Test
    void revokeOwnSessionRevoqueLaSessionEtSesTokensEtJournaliseUneSeuleFois() {
        UserSession session = activeSession(5L, "public-5", LocalDateTime.now(FIXED_CLOCK));
        when(userSessionRepository.findByPublicId("public-5")).thenReturn(Optional.of(session));
        when(userSessionRepository.revokeById(eq(5L), any(), eq("SESSION_REVOKED"))).thenReturn(1);

        try (AuditLogCapture capture = new AuditLogCapture()) {
            SessionService.RevocationOutcome outcome = service.revokeOwnSession(1L, "public-5", 999L, "1");

            assertThat(outcome.revoked()).isTrue();
            assertThat(outcome.wasCurrentSession()).isFalse();
            assertThat(capture.messages()).hasSize(1);
            assertThat(capture.messages().get(0)).contains("eventType=SESSION_REVOKED")
                    .contains("targetId=public-5")
                    .contains("reasonCode=OTHER");
        }
        verify(refreshTokenRepository).revokeAllForSession(eq(5L), any());
    }

    @Test
    void revokeOwnSessionDetecteQuandCEstLaSessionCourante() {
        UserSession session = activeSession(5L, "public-5", LocalDateTime.now(FIXED_CLOCK));
        when(userSessionRepository.findByPublicId("public-5")).thenReturn(Optional.of(session));
        when(userSessionRepository.revokeById(eq(5L), any(), any())).thenReturn(1);

        SessionService.RevocationOutcome outcome = service.revokeOwnSession(1L, "public-5", 5L, "1");

        assertThat(outcome.wasCurrentSession()).isTrue();
    }

    @Test
    void revokeOwnSessionPourUneAutreUtilisateurLeveSessionNotFoundEtJournaliseUnRefus() {
        UserSession sessionDUnAutre = UserSession.builder().id(5L).publicId("public-5").userId(2L)
                .createdAt(LocalDateTime.now(FIXED_CLOCK)).lastUsedAt(LocalDateTime.now(FIXED_CLOCK))
                .expiresAt(LocalDateTime.now(FIXED_CLOCK).plusDays(1)).build();
        when(userSessionRepository.findByPublicId("public-5")).thenReturn(Optional.of(sessionDUnAutre));

        try (AuditLogCapture capture = new AuditLogCapture()) {
            assertThatThrownBy(() -> service.revokeOwnSession(1L, "public-5", 999L, "1"))
                    .isInstanceOf(SessionNotFoundException.class);

            assertThat(capture.messages().get(0)).contains("eventType=SESSION_REVOCATION_DENIED")
                    .contains("result=DENIED")
                    .contains("reasonCode=NOT_FOUND_OR_NOT_OWNED");
        }
        verify(userSessionRepository, never()).revokeById(anyLong(), any(), any());
    }

    @Test
    void revokeOwnSessionInconnueLeveSessionNotFound() {
        when(userSessionRepository.findByPublicId("inconnu")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revokeOwnSession(1L, "inconnu", 999L, "1"))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void revokeOwnSessionAvecUnIdVideLeveInvalidSessionId() {
        assertThatThrownBy(() -> service.revokeOwnSession(1L, "  ", 999L, "1"))
                .isInstanceOf(InvalidSessionIdException.class);

        verifyNoInteractions(userSessionRepository);
    }

    // ===== revokeOthers =====

    @Test
    void revokeOthersConserveLaSessionCouranteEtRevoqueLesAutres() {
        when(userSessionRepository.revokeAllForUserExcept(eq(1L), eq(5L), any(), eq("OTHER_SESSIONS_REVOKED")))
                .thenReturn(3);

        int revoked = service.revokeOthers(1L, 5L, "1");

        assertThat(revoked).isEqualTo(3);
        verify(refreshTokenRepository).revokeAllForUserExceptSession(eq(1L), eq(5L), any());
        verify(refreshTokenRepository, never()).revokeAllForUser(any(), any());
    }

    @Test
    void revokeOthersSansAutreSessionEstUnNoOpIdempotent() {
        when(userSessionRepository.revokeAllForUserExcept(eq(1L), eq(5L), any(), any())).thenReturn(0);

        int revoked = service.revokeOthers(1L, 5L, "1");

        assertThat(revoked).isZero();
    }

    // ===== revokeAll =====

    @Test
    void revokeAllRevoqueToutesLesSessionsYComprisLaCouranteEtJournaliseUneFois() {
        when(userSessionRepository.revokeAllForUser(eq(1L), any(), eq("ALL_SESSIONS_REVOKED"))).thenReturn(4);

        try (AuditLogCapture capture = new AuditLogCapture()) {
            int revoked = service.revokeAll(1L, "1");

            assertThat(revoked).isEqualTo(4);
            assertThat(capture.messages()).hasSize(1);
            assertThat(capture.messages().get(0)).contains("eventType=ALL_SESSIONS_REVOKED").contains("reasonCode=4");
        }
        verify(refreshTokenRepository).revokeAllForUser(eq(1L), any());
    }

    // ===== cleanup =====

    @Test
    void cleanupPurgeLesSessionsRevoqueesEtExpireesAuDelaDeLaRetention() {
        when(userSessionRepository.deleteRevokedBefore(any())).thenReturn(2);
        when(userSessionRepository.deleteExpiredAndNeverRevokedBefore(any())).thenReturn(1);

        try (AuditLogCapture capture = new AuditLogCapture()) {
            SessionService.CleanupResult result = service.cleanup(Duration.ofDays(7));

            assertThat(result.revokedSessionsDeleted()).isEqualTo(2);
            assertThat(result.expiredSessionsDeleted()).isEqualTo(1);
            assertThat(capture.messages()).hasSize(1);
            assertThat(capture.messages().get(0)).contains("eventType=SESSION_CLEANUP_COMPLETED")
                    .doesNotContain("Bearer").doesNotContain("token");
        }
    }

    @Test
    void cleanupSansRienAPurgerNeJournaliseRien() {
        when(userSessionRepository.deleteRevokedBefore(any())).thenReturn(0);
        when(userSessionRepository.deleteExpiredAndNeverRevokedBefore(any())).thenReturn(0);

        try (AuditLogCapture capture = new AuditLogCapture()) {
            service.cleanup(Duration.ofDays(7));

            assertThat(capture.messages()).isEmpty();
        }
    }

    // ===== revocation silencieuse (logout / reuse-detection / password-change) =====

    @Test
    void revokeSessionSilentlyNeJournalisePas() {
        try (AuditLogCapture capture = new AuditLogCapture()) {
            service.revokeSessionSilently(5L, "LOGOUT");

            assertThat(capture.messages()).isEmpty();
        }
        verify(userSessionRepository).revokeById(eq(5L), any(), eq("LOGOUT"));
        verify(refreshTokenRepository).revokeAllForSession(eq(5L), any());
    }

    @Test
    void revokeAllSessionsSilentlyNeJournalisePas() {
        try (AuditLogCapture capture = new AuditLogCapture()) {
            service.revokeAllSessionsSilently(1L, "REFRESH_TOKEN_REUSE_DETECTED");

            assertThat(capture.messages()).isEmpty();
        }
        verify(userSessionRepository).revokeAllForUser(eq(1L), any(), eq("REFRESH_TOKEN_REUSE_DETECTED"));
        verify(refreshTokenRepository).revokeAllForUser(eq(1L), any());
    }

    // ===== touchSession =====

    @Test
    void touchSessionMetAJourLaDerniereActiviteEtRetourneLaSessionAJour() {
        UserSession updated = activeSession(5L, "public-5", LocalDateTime.now(FIXED_CLOCK));
        when(userSessionRepository.findById(5L)).thenReturn(Optional.of(updated));

        UserSession result = service.touchSession(5L, LocalDateTime.now(FIXED_CLOCK).plusDays(7), "5.6.7.8");

        assertThat(result.getPublicId()).isEqualTo("public-5");
        verify(userSessionRepository).touch(eq(5L), any(), any(), eq("5.6.7.8"));
    }
}
