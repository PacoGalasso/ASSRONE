package ASSRONE.backend.service;

import ASSRONE.backend.model.User;
import ASSRONE.backend.repository.UserInfoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies LoginAttemptService's orchestration only (which arguments reach the
 * atomic UPDATE, when a write is skipped). The actual lockout state machine —
 * threshold reached, active lock not re-incremented, expired lock resetting to
 * attempt #1 — is a PostgreSQL-level guarantee proven by
 * LoginLockoutIntegrationTest against real Postgres, not simulated here.
 */
@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    private static final ZoneOffset ZONE = ZoneOffset.UTC;
    private static final String EMAIL = "jean.dupont@assrone.ch";

    @Mock
    private UserInfoRepository repository;

    private MutableClock clock;
    private LoginAttemptService service;

    private void setUp(Instant now) {
        clock = new MutableClock(now, ZONE);
        service = new LoginAttemptService(repository, clock);
    }

    @Test
    void premierEchecAppelleLUpdateAtomiqueAvecLeSeuilEtLaFenetreAttendus() {
        setUp(Instant.parse("2026-01-01T10:00:00Z"));
        when(repository.findByEmail(EMAIL)).thenReturn(Optional.of(User.builder().email(EMAIL).build()));

        service.registerFailedAttempt(EMAIL);

        ArgumentCaptor<LocalDateTime> nowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> lockUntilCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).registerFailedLoginAttempt(eq(EMAIL), nowCaptor.capture(), eq(5), lockUntilCaptor.capture());

        LocalDateTime expectedNow = LocalDateTime.now(clock);
        assertThat(nowCaptor.getValue()).isEqualTo(expectedNow);
        assertThat(lockUntilCaptor.getValue()).isEqualTo(expectedNow.plusMinutes(15));
    }

    @Test
    void plusieursEchecsAppellentLUpdateAChaqueAppel() {
        setUp(Instant.parse("2026-01-01T10:00:00Z"));
        when(repository.findByEmail(EMAIL)).thenReturn(Optional.of(User.builder().email(EMAIL).build()));

        service.registerFailedAttempt(EMAIL);
        service.registerFailedAttempt(EMAIL);
        service.registerFailedAttempt(EMAIL);

        verify(repository, times(3)).registerFailedLoginAttempt(eq(EMAIL), any(), eq(5), any());
    }

    @Test
    void utilisateurInexistantNeDeclencheAucuneEcriture() {
        setUp(Instant.parse("2026-01-01T10:00:00Z"));
        when(repository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        service.registerFailedAttempt(EMAIL);

        verify(repository, never()).registerFailedLoginAttempt(any(), any(), anyInt(), any());
    }

    @Test
    void emailNullNeDeclencheAucuneEcritureEtNeConsultePasLeRepository() {
        setUp(Instant.parse("2026-01-01T10:00:00Z"));

        service.registerFailedAttempt(null);
        service.resetFailedAttempts(null);

        verifyNoInteractions(repository);
    }

    @Test
    void succesReinitialiseLeCompteurAvecLEmailFourni() {
        setUp(Instant.parse("2026-01-01T10:00:00Z"));

        service.resetFailedAttempts(EMAIL);

        verify(repository).resetFailedLoginAttempts(EMAIL);
    }

    @Test
    void succesReinitialiseMemeSiAucunEchecPrealableNestConnu() {
        setUp(Instant.parse("2026-01-01T10:00:00Z"));

        service.resetFailedAttempts(EMAIL);

        verify(repository, times(1)).resetFailedLoginAttempts(EMAIL);
    }

    @Test
    void lHorlogeInjecteeDetermineLeVerrouCalcule() {
        setUp(Instant.parse("2026-06-15T08:30:00Z"));
        when(repository.findByEmail(EMAIL)).thenReturn(Optional.of(User.builder().email(EMAIL).build()));

        service.registerFailedAttempt(EMAIL);

        ArgumentCaptor<LocalDateTime> lockUntilCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).registerFailedLoginAttempt(eq(EMAIL), any(), eq(5), lockUntilCaptor.capture());
        assertThat(lockUntilCaptor.getValue()).isEqualTo(LocalDateTime.now(clock).plusMinutes(15));
    }

    @Test
    void avancerLHorlogeChangeLeCalculDuProchainVerrou() {
        setUp(Instant.parse("2026-01-01T10:00:00Z"));
        when(repository.findByEmail(EMAIL)).thenReturn(Optional.of(User.builder().email(EMAIL).build()));

        clock.advance(Duration.ofHours(2));
        service.registerFailedAttempt(EMAIL);

        ArgumentCaptor<LocalDateTime> nowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).registerFailedLoginAttempt(eq(EMAIL), nowCaptor.capture(), eq(5), any());
        assertThat(nowCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 1, 1, 12, 0));
    }

    /**
     * Guards against the exact regression this test class's sibling
     * (LoginLockoutIntegrationTest) once caught at the Testcontainers level:
     * @Transactional only takes effect through Spring's AOP proxy around a
     * container-managed bean — a plain "new LoginAttemptService(...)" never
     * gets one. This fast, Docker-independent check fails immediately if
     * either method ever loses the annotation, without waiting for a real
     * Postgres-backed flush failure to surface it.
     */
    @Test
    void lesMethodesModifiantLetatSontAnnoteesTransactional() throws NoSuchMethodException {
        Method registerFailedAttempt = LoginAttemptService.class.getMethod("registerFailedAttempt", String.class);
        Method resetFailedAttempts = LoginAttemptService.class.getMethod("resetFailedAttempts", String.class);

        assertThat(registerFailedAttempt.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(resetFailedAttempts.isAnnotationPresent(Transactional.class)).isTrue();
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}
