package ASSRONE.backend.service;

import ASSRONE.backend.exception.InvalidRefreshTokenException;
import ASSRONE.backend.model.RefreshToken;
import ASSRONE.backend.model.User;
import ASSRONE.backend.repository.RefreshTokenRepository;
import ASSRONE.backend.repository.UserInfoRepository;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {

    private static final Instant NOW_INSTANT = Instant.parse("2026-08-05T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW_INSTANT, ZoneOffset.UTC);
    private static final String EMAIL = "membre@assrone.ch";

    private RefreshTokenRepository refreshTokenRepository;
    private UserInfoRepository userInfoRepository;
    private JwtService jwtService;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        userInfoRepository = mock(UserInfoRepository.class);
        jwtService = mock(JwtService.class);
        service = new RefreshTokenService(refreshTokenRepository, userInfoRepository, jwtService, FIXED_CLOCK);
    }

    private static User usableUser() {
        return User.builder()
                .id(1L)
                .email(EMAIL)
                .username("membre")
                .password("hash")
                .role("USER")
                .isActive(true)
                .build();
    }

    private static RefreshToken storedToken(String jti, String tokenHash, LocalDateTime expiresAt, LocalDateTime revokedAt) {
        return RefreshToken.builder()
                .id(10L)
                .userId(1L)
                .jti(jti)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .revokedAt(revokedAt)
                .build();
    }

    private static String sha256Hex(String raw) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hashed);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    // ===== issueTokens =====

    @Test
    void issueTokensMintUnCoupleDeTokensEtPersisteLeHashDuRefresh() {
        User user = usableUser();
        when(userInfoRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(jwtService.generateToken(EMAIL)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(eq(EMAIL), anyString())).thenReturn("refresh-token");
        when(jwtService.extractExpiration("refresh-token"))
                .thenReturn(Date.from(NOW_INSTANT.plusSeconds(3600)));

        RefreshTokenService.IssuedTokens tokens = service.issueTokens(EMAIL);

        assertThat(tokens.accessToken()).isEqualTo("access-token");
        assertThat(tokens.refreshToken()).isEqualTo("refresh-token");
        assertThat(tokens.role()).isEqualTo("ROLE_USER");
        assertThat(tokens.email()).isEqualTo(EMAIL);
        assertThat(tokens.refreshTokenMaxAge()).isEqualTo(Duration.ofSeconds(3600));

        var captor = org.mockito.ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getTokenHash()).isEqualTo(sha256Hex("refresh-token"));
        assertThat(saved.getTokenHash()).isNotEqualTo("refresh-token");
    }

    @Test
    void issueTokensAvecUtilisateurInexistantEstRefuse() {
        when(userInfoRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issueTokens(EMAIL))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verifyNoInteractions(refreshTokenRepository);
    }

    // ===== rotate : succès + rotation =====

    @Test
    void rotateAvecUnRefreshTokenValideEmetUnNouveauCoupleEtRevoqueLAncien() {
        User user = usableUser();
        String presented = "refresh-token-valide";
        String hash = sha256Hex(presented);
        RefreshToken stored = storedToken("jti-1", hash, LocalDateTime.now(FIXED_CLOCK).plusDays(1), null);

        when(jwtService.extractTokenType(presented)).thenReturn(JwtService.TOKEN_TYPE_REFRESH);
        when(jwtService.extractUsername(presented)).thenReturn(EMAIL);
        when(jwtService.extractJti(presented)).thenReturn("jti-1");
        when(refreshTokenRepository.findByJti("jti-1")).thenReturn(Optional.of(stored));
        when(userInfoRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(jwtService.generateToken(EMAIL)).thenReturn("nouveau-access-token");
        when(jwtService.generateRefreshToken(eq(EMAIL), anyString())).thenReturn("nouveau-refresh-token");
        when(jwtService.extractExpiration("nouveau-refresh-token"))
                .thenReturn(Date.from(NOW_INSTANT.plusSeconds(3600)));

        RefreshTokenService.IssuedTokens tokens = service.rotate(presented);

        assertThat(tokens.accessToken()).isEqualTo("nouveau-access-token");
        assertThat(tokens.refreshToken()).isEqualTo("nouveau-refresh-token");
        assertThat(tokens.refreshTokenMaxAge()).isEqualTo(Duration.ofSeconds(3600));
        verify(refreshTokenRepository).revokeByJti(eq("jti-1"), any());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    // ===== rotate : cas négatifs =====

    @Test
    void rotateAvecUnAccessTokenPresenteCommeRefreshEstRefuse() {
        String accessToken = "un-access-token";
        when(jwtService.extractTokenType(accessToken)).thenReturn(JwtService.TOKEN_TYPE_ACCESS);
        when(jwtService.extractUsername(accessToken)).thenReturn(EMAIL);
        when(jwtService.extractJti(accessToken)).thenReturn(null);

        assertThatThrownBy(() -> service.rotate(accessToken))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void rotateAvecUnTokenMalFormeEstRefuse() {
        String malforme = "pas-un-jwt";
        when(jwtService.extractTokenType(malforme)).thenThrow(new JwtException("signature invalide"));

        assertThatThrownBy(() -> service.rotate(malforme))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void rotateAvecUnTokenExpireEstRefuse() {
        String presented = "refresh-expire";
        String hash = sha256Hex(presented);
        RefreshToken stored = storedToken("jti-2", hash, LocalDateTime.now(FIXED_CLOCK).minusDays(1), null);

        when(jwtService.extractTokenType(presented)).thenReturn(JwtService.TOKEN_TYPE_REFRESH);
        when(jwtService.extractUsername(presented)).thenReturn(EMAIL);
        when(jwtService.extractJti(presented)).thenReturn("jti-2");
        when(refreshTokenRepository.findByJti("jti-2")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.rotate(presented))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository, never()).revokeByJti(anyString(), any());
    }

    @Test
    void rotateAvecUnJtiInconnuEstRefuse() {
        String presented = "refresh-jti-inconnu";
        when(jwtService.extractTokenType(presented)).thenReturn(JwtService.TOKEN_TYPE_REFRESH);
        when(jwtService.extractUsername(presented)).thenReturn(EMAIL);
        when(jwtService.extractJti(presented)).thenReturn("jti-inconnu");
        when(refreshTokenRepository.findByJti("jti-inconnu")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate(presented))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void rotateAvecUnHashQuiNeCorrespondPasEstRefuse() {
        String presented = "refresh-token-different-du-hash-stocke";
        RefreshToken stored = storedToken("jti-3", sha256Hex("un-autre-token"), LocalDateTime.now(FIXED_CLOCK).plusDays(1), null);

        when(jwtService.extractTokenType(presented)).thenReturn(JwtService.TOKEN_TYPE_REFRESH);
        when(jwtService.extractUsername(presented)).thenReturn(EMAIL);
        when(jwtService.extractJti(presented)).thenReturn("jti-3");
        when(refreshTokenRepository.findByJti("jti-3")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.rotate(presented))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository, never()).revokeByJti(anyString(), any());
    }

    @Test
    void rotateAvecUnCompteDesactiveEstRefuse() {
        User disabled = usableUser();
        disabled.setIsActive(false);
        String presented = "refresh-compte-desactive";
        String hash = sha256Hex(presented);
        RefreshToken stored = storedToken("jti-4", hash, LocalDateTime.now(FIXED_CLOCK).plusDays(1), null);

        when(jwtService.extractTokenType(presented)).thenReturn(JwtService.TOKEN_TYPE_REFRESH);
        when(jwtService.extractUsername(presented)).thenReturn(EMAIL);
        when(jwtService.extractJti(presented)).thenReturn("jti-4");
        when(refreshTokenRepository.findByJti("jti-4")).thenReturn(Optional.of(stored));
        when(userInfoRepository.findByEmail(EMAIL)).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> service.rotate(presented))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository, never()).revokeByJti(anyString(), any());
    }

    @Test
    void rotateAvecUnCompteVerrouilleEstRefuse() {
        User locked = usableUser();
        locked.setLockedUntil(LocalDateTime.now(FIXED_CLOCK).plusMinutes(30));
        String presented = "refresh-compte-verrouille";
        String hash = sha256Hex(presented);
        RefreshToken stored = storedToken("jti-5", hash, LocalDateTime.now(FIXED_CLOCK).plusDays(1), null);

        when(jwtService.extractTokenType(presented)).thenReturn(JwtService.TOKEN_TYPE_REFRESH);
        when(jwtService.extractUsername(presented)).thenReturn(EMAIL);
        when(jwtService.extractJti(presented)).thenReturn("jti-5");
        when(refreshTokenRepository.findByJti("jti-5")).thenReturn(Optional.of(stored));
        when(userInfoRepository.findByEmail(EMAIL)).thenReturn(Optional.of(locked));

        assertThatThrownBy(() -> service.rotate(presented))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository, never()).revokeByJti(anyString(), any());
    }

    @Test
    void rotateAvecUnUtilisateurSupprimeEstRefuseSansErreur500() {
        String presented = "refresh-utilisateur-supprime";
        String hash = sha256Hex(presented);
        RefreshToken stored = storedToken("jti-6", hash, LocalDateTime.now(FIXED_CLOCK).plusDays(1), null);

        when(jwtService.extractTokenType(presented)).thenReturn(JwtService.TOKEN_TYPE_REFRESH);
        when(jwtService.extractUsername(presented)).thenReturn(EMAIL);
        when(jwtService.extractJti(presented)).thenReturn("jti-6");
        when(refreshTokenRepository.findByJti("jti-6")).thenReturn(Optional.of(stored));
        when(userInfoRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate(presented))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void reutilisationDUnJtiDejaRevoqueRevoqueTousLesTokensDeLUtilisateur() {
        String presented = "refresh-deja-revoque";
        String hash = sha256Hex(presented);
        RefreshToken stored = storedToken("jti-7", hash, LocalDateTime.now(FIXED_CLOCK).plusDays(1),
                LocalDateTime.now(FIXED_CLOCK).minusMinutes(5));

        when(jwtService.extractTokenType(presented)).thenReturn(JwtService.TOKEN_TYPE_REFRESH);
        when(jwtService.extractUsername(presented)).thenReturn(EMAIL);
        when(jwtService.extractJti(presented)).thenReturn("jti-7");
        when(refreshTokenRepository.findByJti("jti-7")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.rotate(presented))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository, times(1)).revokeAllForUser(eq(1L), any());
        verify(refreshTokenRepository, never()).revokeByJti(anyString(), any());
    }

    // ===== revoke (logout) =====

    @Test
    void revokeAvecUnTokenValideRevoqueSonJti() {
        String presented = "refresh-a-revoquer";
        when(jwtService.extractJti(presented)).thenReturn("jti-8");

        service.revoke(presented);

        verify(refreshTokenRepository).revokeByJti(eq("jti-8"), any());
    }

    @Test
    void revokeAvecUnTokenNullNeFaitRien() {
        service.revoke(null);

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void revokeAvecUnTokenVideNeFaitRien() {
        service.revoke("   ");

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void revokeAvecUnTokenMalFormeNeFaitRienEtNeLeveRien() {
        String malforme = "pas-un-jwt";
        when(jwtService.extractJti(malforme)).thenThrow(new JwtException("signature invalide"));

        service.revoke(malforme);

        verifyNoInteractions(refreshTokenRepository);
    }
}
