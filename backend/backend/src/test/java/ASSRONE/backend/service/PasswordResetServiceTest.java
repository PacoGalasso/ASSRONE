package ASSRONE.backend.service;

import ASSRONE.backend.audit.SecurityAuditService;
import ASSRONE.backend.audit.SecurityEventResult;
import ASSRONE.backend.audit.SecurityEventType;
import ASSRONE.backend.config.AccountLifecycleProperties;
import ASSRONE.backend.event.PasswordResetRequestedEvent;
import ASSRONE.backend.exception.InvalidPasswordException;
import ASSRONE.backend.exception.InvalidPasswordResetTokenException;
import ASSRONE.backend.model.PasswordResetToken;
import ASSRONE.backend.model.User;
import ASSRONE.backend.repository.PasswordResetTokenRepository;
import ASSRONE.backend.repository.UserInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

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

class PasswordResetServiceTest {

    private static final Instant NOW_INSTANT = Instant.parse("2026-08-06T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW_INSTANT, ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(FIXED_CLOCK);

    private UserInfoRepository userInfoRepository;
    private PasswordResetTokenRepository tokenRepository;
    private PasswordEncoder passwordEncoder;
    private RefreshTokenService refreshTokenService;
    private SecurityAuditService securityAuditService;
    private ApplicationEventPublisher eventPublisher;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        userInfoRepository = mock(UserInfoRepository.class);
        tokenRepository = mock(PasswordResetTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        refreshTokenService = mock(RefreshTokenService.class);
        securityAuditService = mock(SecurityAuditService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        AccountLifecycleProperties properties = new AccountLifecycleProperties(
                "http://localhost:4200",
                new AccountLifecycleProperties.ResetToken(Duration.ofHours(1)),
                new AccountLifecycleProperties.VerificationToken(Duration.ofHours(24)),
                new AccountLifecycleProperties.Cleanup(true, "0 30 3 * * *", Duration.ofDays(7)));

        service = new PasswordResetService(userInfoRepository, tokenRepository, passwordEncoder, refreshTokenService,
                properties, securityAuditService, eventPublisher, FIXED_CLOCK);
    }

    private User existingUser() {
        User user = new User();
        user.setId(1L);
        user.setEmail("membre@assrone.ch");
        user.setPassword("hash-existant");
        return user;
    }

    // ===== requestReset : anti-énumération =====

    @Test
    void demandePourUnCompteInexistantNecritAucunTokenEtNenvoieAucunEmail() {
        when(userInfoRepository.findByEmail("inconnu@assrone.ch")).thenReturn(Optional.empty());

        service.requestReset("inconnu@assrone.ch");

        verifyNoInteractions(tokenRepository, eventPublisher);
    }

    @Test
    void demandePourUnCompteInexistantJournaliseQuandMemeUnAuditSansToken() {
        when(userInfoRepository.findByEmail("inconnu@assrone.ch")).thenReturn(Optional.empty());

        service.requestReset("inconnu@assrone.ch");

        verify(securityAuditService).record(SecurityEventType.PASSWORD_RESET_REQUESTED, SecurityEventResult.SUCCESS,
                "-", null, "user", null, "ACCOUNT_NOT_FOUND");
    }

    @Test
    void demandeNormaliseLEmailAvantLaRecherche() {
        when(userInfoRepository.findByEmail("membre@assrone.ch")).thenReturn(Optional.empty());

        service.requestReset("  Membre@ASSRONE.ch  ");

        verify(userInfoRepository).findByEmail("membre@assrone.ch");
    }

    @Test
    void demandePourUnCompteExistantCreeUnTokenHacheJamaisEnClair() {
        User user = existingUser();
        when(userInfoRepository.findByEmail("membre@assrone.ch")).thenReturn(Optional.of(user));

        service.requestReset("membre@assrone.ch");

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(captor.capture());
        PasswordResetToken saved = captor.getValue();
        assertThat(saved.getTokenHash()).hasSize(64).matches("^[0-9a-f]{64}$");
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plusHours(1));
    }

    @Test
    void demandePourUnCompteExistantInvalideLesTokensActifsPrecedents() {
        User user = existingUser();
        when(userInfoRepository.findByEmail("membre@assrone.ch")).thenReturn(Optional.of(user));

        service.requestReset("membre@assrone.ch");

        verify(tokenRepository).invalidateAllActiveForUser(eq(1L), any());
    }

    @Test
    void demandePourUnCompteExistantPublieLevenementAvecLeTokenEnClairPourLemail() {
        User user = existingUser();
        when(userInfoRepository.findByEmail("membre@assrone.ch")).thenReturn(Optional.of(user));

        service.requestReset("membre@assrone.ch");

        ArgumentCaptor<PasswordResetRequestedEvent> captor = ArgumentCaptor.forClass(PasswordResetRequestedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo("membre@assrone.ch");
        assertThat(captor.getValue().rawToken()).isNotBlank();
    }

    // ===== resetPassword =====

    @Test
    void resetAvecUnTokenInconnuEstRefuse() {
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("token-inconnu", "nouveauMotDePasse123"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);

        verify(userInfoRepository, never()).save(any());
    }

    @Test
    void resetAvecUnTokenExpireEstRefuse() {
        PasswordResetToken token = PasswordResetToken.builder()
                .id(10L).userId(1L).tokenHash("hash").createdAt(NOW.minusHours(2)).expiresAt(NOW.minusHours(1)).build();
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(tokenRepository.markUsedIfValid(eq(10L), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.resetPassword("token-expire", "nouveauMotDePasse123"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);

        verify(userInfoRepository, never()).save(any());
    }

    @Test
    void resetAvecUnTokenDejaUtiliseEstRefuse() {
        PasswordResetToken token = PasswordResetToken.builder()
                .id(10L).userId(1L).tokenHash("hash").createdAt(NOW.minusHours(2))
                .expiresAt(NOW.plusHours(1)).usedAt(NOW.minusMinutes(5)).build();
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(tokenRepository.markUsedIfValid(eq(10L), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.resetPassword("token-deja-utilise", "nouveauMotDePasse123"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    @Test
    void resetValideEncodeEtSauvegardeLeNouveauMotDePasse() {
        User user = existingUser();
        PasswordResetToken token = PasswordResetToken.builder()
                .id(10L).userId(1L).tokenHash("hash").createdAt(NOW.minusMinutes(5)).expiresAt(NOW.plusHours(1)).build();
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(tokenRepository.markUsedIfValid(eq(10L), any(), any())).thenReturn(1);
        when(userInfoRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("nouveauMotDePasse123")).thenReturn("nouveau-hash");

        service.resetPassword("token-valide", "nouveauMotDePasse123");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userInfoRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("nouveau-hash");
    }

    @Test
    void resetValideInvalideLesAutresTokensDuMemeUtilisateur() {
        User user = existingUser();
        PasswordResetToken token = PasswordResetToken.builder()
                .id(10L).userId(1L).tokenHash("hash").createdAt(NOW.minusMinutes(5)).expiresAt(NOW.plusHours(1)).build();
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(tokenRepository.markUsedIfValid(eq(10L), any(), any())).thenReturn(1);
        when(userInfoRepository.findById(1L)).thenReturn(Optional.of(user));

        service.resetPassword("token-valide", "nouveauMotDePasse123");

        verify(tokenRepository).invalidateAllActiveForUser(eq(1L), any());
    }

    @Test
    void resetValideRevoqueTousLesRefreshTokensDeLUtilisateur() {
        User user = existingUser();
        PasswordResetToken token = PasswordResetToken.builder()
                .id(10L).userId(1L).tokenHash("hash").createdAt(NOW.minusMinutes(5)).expiresAt(NOW.plusHours(1)).build();
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(tokenRepository.markUsedIfValid(eq(10L), any(), any())).thenReturn(1);
        when(userInfoRepository.findById(1L)).thenReturn(Optional.of(user));

        service.resetPassword("token-valide", "nouveauMotDePasse123");

        verify(refreshTokenService).revokeAllForUser(1L);
    }

    @Test
    void resetValideJournaliseLeSucces() {
        User user = existingUser();
        PasswordResetToken token = PasswordResetToken.builder()
                .id(10L).userId(1L).tokenHash("hash").createdAt(NOW.minusMinutes(5)).expiresAt(NOW.plusHours(1)).build();
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(tokenRepository.markUsedIfValid(eq(10L), any(), any())).thenReturn(1);
        when(userInfoRepository.findById(1L)).thenReturn(Optional.of(user));

        service.resetPassword("token-valide", "nouveauMotDePasse123");

        verify(securityAuditService).record(SecurityEventType.PASSWORD_RESET_SUCCEEDED, SecurityEventResult.SUCCESS,
                "1", null, "user", "1", null);
    }

    @Test
    void resetAvecUnMotDePasseTropCourtEstRefuseAvantToutEnregistrement() {
        User user = existingUser();
        PasswordResetToken token = PasswordResetToken.builder()
                .id(10L).userId(1L).tokenHash("hash").createdAt(NOW.minusMinutes(5)).expiresAt(NOW.plusHours(1)).build();
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(tokenRepository.markUsedIfValid(eq(10L), any(), any())).thenReturn(1);
        when(userInfoRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.resetPassword("token-valide", "court"))
                .isInstanceOf(InvalidPasswordException.class);

        verify(userInfoRepository, never()).save(any());
    }
}
