package ASSRONE.backend.service;

import ASSRONE.backend.audit.SecurityAuditService;
import ASSRONE.backend.audit.SecurityEventResult;
import ASSRONE.backend.audit.SecurityEventType;
import ASSRONE.backend.config.AccountLifecycleProperties;
import ASSRONE.backend.event.EmailVerificationRequestedEvent;
import ASSRONE.backend.exception.InvalidEmailVerificationTokenException;
import ASSRONE.backend.model.EmailVerificationToken;
import ASSRONE.backend.model.User;
import ASSRONE.backend.repository.EmailVerificationTokenRepository;
import ASSRONE.backend.repository.UserInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EmailVerificationServiceTest {

    private static final Instant NOW_INSTANT = Instant.parse("2026-08-06T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW_INSTANT, ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(FIXED_CLOCK);

    private UserInfoRepository userInfoRepository;
    private EmailVerificationTokenRepository tokenRepository;
    private SecurityAuditService securityAuditService;
    private ApplicationEventPublisher eventPublisher;
    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        userInfoRepository = mock(UserInfoRepository.class);
        tokenRepository = mock(EmailVerificationTokenRepository.class);
        securityAuditService = mock(SecurityAuditService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        AccountLifecycleProperties properties = new AccountLifecycleProperties(
                "http://localhost:4200",
                new AccountLifecycleProperties.ResetToken(Duration.ofHours(1)),
                new AccountLifecycleProperties.VerificationToken(Duration.ofHours(24)),
                new AccountLifecycleProperties.Cleanup(true, "0 30 3 * * *", Duration.ofDays(7)));

        service = new EmailVerificationService(userInfoRepository, tokenRepository, properties, securityAuditService,
                eventPublisher, FIXED_CLOCK);
    }

    private User unverifiedUser() {
        User user = new User();
        user.setId(1L);
        user.setEmail("membre@assrone.ch");
        user.setEmailVerifiedAt(null);
        return user;
    }

    // ===== issueTokenAndSendEmail (appelé depuis l'inscription ou un changement d'email) =====

    @Test
    void issueTokenAndSendEmailCreeUnTokenHacheEtPublieLEvenement() {
        User user = unverifiedUser();

        service.issueTokenAndSendEmail(user);

        ArgumentCaptor<EmailVerificationToken> tokenCaptor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getTokenHash()).hasSize(64).matches("^[0-9a-f]{64}$");
        assertThat(tokenCaptor.getValue().getExpiresAt()).isEqualTo(NOW.plusHours(24));

        ArgumentCaptor<EmailVerificationRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(EmailVerificationRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().email()).isEqualTo("membre@assrone.ch");
        assertThat(eventCaptor.getValue().rawToken()).isNotBlank();
    }

    @Test
    void issueTokenAndSendEmailAvecUnReasonCodeExplicitePropageCeReasonCodeDansLAudit() {
        User user = unverifiedUser();

        service.issueTokenAndSendEmail(user, "EMAIL_CHANGED");

        verify(securityAuditService).record(SecurityEventType.EMAIL_VERIFICATION_REQUESTED, SecurityEventResult.SUCCESS,
                "1", null, "user", "1", "EMAIL_CHANGED");
    }

    // ===== resend : anti-énumération =====

    @Test
    void resendPourUnCompteInexistantNecritAucunTokenEtNenvoieAucunEmail() {
        when(userInfoRepository.findByEmail("inconnu@assrone.ch")).thenReturn(Optional.empty());

        service.resend("inconnu@assrone.ch");

        verifyNoInteractions(tokenRepository, eventPublisher);
    }

    @Test
    void resendPourUnCompteDejaVerifieNecritAucunTokenEtNenvoieAucunEmail() {
        User user = unverifiedUser();
        user.setEmailVerifiedAt(NOW.minusDays(1));
        when(userInfoRepository.findByEmail("membre@assrone.ch")).thenReturn(Optional.of(user));

        service.resend("membre@assrone.ch");

        verifyNoInteractions(tokenRepository, eventPublisher);
    }

    @Test
    void resendPourUnCompteNonVerifieInvalideLancienTokenEtEnEmetUnNouveau() {
        User user = unverifiedUser();
        when(userInfoRepository.findByEmail("membre@assrone.ch")).thenReturn(Optional.of(user));

        service.resend("membre@assrone.ch");

        verify(tokenRepository).invalidateAllActiveForUser(eq(1L), any());
        verify(tokenRepository).save(any());
        verify(eventPublisher).publishEvent(any(EmailVerificationRequestedEvent.class));
    }

    // ===== verify =====

    @Test
    void verifyAvecUnTokenInconnuEstRefuse() {
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify("token-inconnu"))
                .isInstanceOf(InvalidEmailVerificationTokenException.class);

        verify(userInfoRepository, never()).save(any());
    }

    @Test
    void verifyAvecUnTokenExpireOuDejaUtiliseEstRefuse() {
        EmailVerificationToken token = EmailVerificationToken.builder()
                .id(20L).userId(1L).tokenHash("hash").createdAt(NOW.minusHours(25)).expiresAt(NOW.minusHours(1)).build();
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(tokenRepository.markUsedIfValid(eq(20L), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.verify("token-expire"))
                .isInstanceOf(InvalidEmailVerificationTokenException.class);

        verify(userInfoRepository, never()).save(any());
    }

    @Test
    void verifyValideMarqueLeCompteVerifie() {
        User user = unverifiedUser();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .id(20L).userId(1L).tokenHash("hash").createdAt(NOW.minusMinutes(5)).expiresAt(NOW.plusHours(1)).build();
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(tokenRepository.markUsedIfValid(eq(20L), any(), any())).thenReturn(1);
        when(userInfoRepository.findById(1L)).thenReturn(Optional.of(user));

        service.verify("token-valide");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userInfoRepository).save(captor.capture());
        assertThat(captor.getValue().getEmailVerifiedAt()).isEqualTo(NOW);
    }

    @Test
    void verifyValideInvalideLesAutresTokensDuMemeUtilisateur() {
        User user = unverifiedUser();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .id(20L).userId(1L).tokenHash("hash").createdAt(NOW.minusMinutes(5)).expiresAt(NOW.plusHours(1)).build();
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(tokenRepository.markUsedIfValid(eq(20L), any(), any())).thenReturn(1);
        when(userInfoRepository.findById(1L)).thenReturn(Optional.of(user));

        service.verify("token-valide");

        verify(tokenRepository).invalidateAllActiveForUser(eq(1L), any());
    }

    @Test
    void verifyValideJournaliseLeSucces() {
        User user = unverifiedUser();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .id(20L).userId(1L).tokenHash("hash").createdAt(NOW.minusMinutes(5)).expiresAt(NOW.plusHours(1)).build();
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(tokenRepository.markUsedIfValid(eq(20L), any(), any())).thenReturn(1);
        when(userInfoRepository.findById(1L)).thenReturn(Optional.of(user));

        service.verify("token-valide");

        verify(securityAuditService).record(SecurityEventType.EMAIL_VERIFIED, SecurityEventResult.SUCCESS,
                "1", null, "user", "1", null);
    }

    @Test
    void reverifierUnTokenDejaConsommeEstIdempotentEtNeRevelePasQueLeCompteEtaitDejaVerifie() {
        User user = unverifiedUser();
        user.setEmailVerifiedAt(NOW.minusMinutes(1));
        EmailVerificationToken token = EmailVerificationToken.builder()
                .id(20L).userId(1L).tokenHash("hash").createdAt(NOW.minusMinutes(5))
                .expiresAt(NOW.plusHours(1)).usedAt(NOW.minusMinutes(1)).build();
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(tokenRepository.markUsedIfValid(eq(20L), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.verify("token-deja-consomme"))
                .isInstanceOf(InvalidEmailVerificationTokenException.class);
    }
}
