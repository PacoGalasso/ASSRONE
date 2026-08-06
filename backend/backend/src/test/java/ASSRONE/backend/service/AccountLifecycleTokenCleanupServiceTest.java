package ASSRONE.backend.service;

import ASSRONE.backend.audit.SecurityAuditService;
import ASSRONE.backend.audit.SecurityEventResult;
import ASSRONE.backend.audit.SecurityEventType;
import ASSRONE.backend.config.AccountLifecycleProperties;
import ASSRONE.backend.repository.EmailVerificationTokenRepository;
import ASSRONE.backend.repository.PasswordResetTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountLifecycleTokenCleanupServiceTest {

    private static final Instant NOW_INSTANT = Instant.parse("2026-08-06T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW_INSTANT, ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(FIXED_CLOCK);

    private PasswordResetTokenRepository passwordResetTokenRepository;
    private EmailVerificationTokenRepository emailVerificationTokenRepository;
    private SecurityAuditService securityAuditService;
    private AccountLifecycleTokenCleanupService service;

    @BeforeEach
    void setUp() {
        passwordResetTokenRepository = mock(PasswordResetTokenRepository.class);
        emailVerificationTokenRepository = mock(EmailVerificationTokenRepository.class);
        securityAuditService = mock(SecurityAuditService.class);
        AccountLifecycleProperties properties = new AccountLifecycleProperties(
                "http://localhost:4200",
                new AccountLifecycleProperties.ResetToken(Duration.ofHours(1)),
                new AccountLifecycleProperties.VerificationToken(Duration.ofHours(24)),
                new AccountLifecycleProperties.Cleanup(true, "0 30 3 * * *", Duration.ofDays(7)));
        service = new AccountLifecycleTokenCleanupService(passwordResetTokenRepository,
                emailVerificationTokenRepository, properties, securityAuditService, FIXED_CLOCK);
    }

    @Test
    void cleanupUtiliseUneSeuleFenetreDeRetentionPourLesTokensUtilisesEtExpires() {
        Duration retention = Duration.ofDays(7);
        LocalDateTime attendu = NOW.minus(retention);

        service.cleanup(retention);

        verify(passwordResetTokenRepository).deleteUsedBefore(attendu);
        verify(passwordResetTokenRepository).deleteExpiredBefore(attendu);
        verify(emailVerificationTokenRepository).deleteUsedBefore(attendu);
        verify(emailVerificationTokenRepository).deleteExpiredBefore(attendu);
    }

    @Test
    void cleanupAgregeLesComptesDesDeuxRepositories() {
        Duration retention = Duration.ofDays(7);
        when(passwordResetTokenRepository.deleteUsedBefore(any())).thenReturn(2);
        when(passwordResetTokenRepository.deleteExpiredBefore(any())).thenReturn(3);
        when(emailVerificationTokenRepository.deleteUsedBefore(any())).thenReturn(1);
        when(emailVerificationTokenRepository.deleteExpiredBefore(any())).thenReturn(4);

        AccountLifecycleTokenCleanupService.CleanupResult result = service.cleanup(retention);

        assertThat(result.passwordResetTokensDeleted()).isEqualTo(5);
        assertThat(result.emailVerificationTokensDeleted()).isEqualTo(5);
    }

    @Test
    void purgeStaleTokensSansAucunTokenAPurgerNecritAucunAudit() {
        when(passwordResetTokenRepository.deleteUsedBefore(any())).thenReturn(0);
        when(passwordResetTokenRepository.deleteExpiredBefore(any())).thenReturn(0);
        when(emailVerificationTokenRepository.deleteUsedBefore(any())).thenReturn(0);
        when(emailVerificationTokenRepository.deleteExpiredBefore(any())).thenReturn(0);

        service.purgeStaleTokens();

        verify(securityAuditService, never()).record(eq(SecurityEventType.ACCOUNT_LIFECYCLE_TOKEN_CLEANUP_COMPLETED),
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void purgeStaleTokensAvecDesTokensPurgesJournaliseUnAuditAgregeSansHash() {
        when(passwordResetTokenRepository.deleteUsedBefore(any())).thenReturn(2);
        when(passwordResetTokenRepository.deleteExpiredBefore(any())).thenReturn(0);
        when(emailVerificationTokenRepository.deleteUsedBefore(any())).thenReturn(0);
        when(emailVerificationTokenRepository.deleteExpiredBefore(any())).thenReturn(0);

        service.purgeStaleTokens();

        verify(securityAuditService).record(eq(SecurityEventType.ACCOUNT_LIFECYCLE_TOKEN_CLEANUP_COMPLETED),
                eq(SecurityEventResult.SUCCESS), eq("-"), eq(null), eq("accountLifecycleTokens"), eq(null),
                eq("PASSWORD_RESET=2;EMAIL_VERIFICATION=0"));
    }
}
