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
import java.time.ZoneId;
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

    // ===== refreshTokenMaxAge : indépendance du fuseau horaire =====
    //
    // Régression : Duration.between(LocalDateTime.now(clock), expiresAt)
    // était faux dès que expiresAt (converti via ZoneId.systemDefault()) et
    // LocalDateTime.now(clock) (zone du clock injecté) ne partageaient pas la
    // même zone — ce qui arrive dès qu'un test utilise un clock UTC sur une
    // machine dont le fuseau par défaut est, par exemple, Europe/Zurich. Le
    // calcul se fait maintenant uniquement à partir d'Instant, donc le fuseau
    // du clock ne doit plus jamais influencer la durée obtenue.

    @Test
    void maxAgeResteDUneHeureEnUTC() {
        Duration maxAge = maxAgeForClockAndExpiration(
                Clock.fixed(NOW_INSTANT, ZoneOffset.UTC), NOW_INSTANT.plusSeconds(3600));

        assertThat(maxAge).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void maxAgeResteDUneHeureEnEuropeZurichEnEte() {
        // 5 août : heure d'été (CEST, UTC+2)
        Duration maxAge = maxAgeForClockAndExpiration(
                Clock.fixed(NOW_INSTANT, ZoneId.of("Europe/Zurich")), NOW_INSTANT.plusSeconds(3600));

        assertThat(maxAge).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void maxAgeResteDUneHeureEnEuropeZurichEnHiver() {
        // 5 janvier : heure d'hiver (CET, UTC+1)
        Instant hiver = Instant.parse("2026-01-05T10:00:00Z");
        Duration maxAge = maxAgeForClockAndExpiration(
                Clock.fixed(hiver, ZoneId.of("Europe/Zurich")), hiver.plusSeconds(3600));

        assertThat(maxAge).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void maxAgeResteDUneHeureAvecUnAutreDecalageHoraireSansHeureDEte() {
        // Asia/Tokyo : UTC+9 fixe, sans heure d'été, décalage différent de Zurich
        Duration maxAge = maxAgeForClockAndExpiration(
                Clock.fixed(NOW_INSTANT, ZoneId.of("Asia/Tokyo")), NOW_INSTANT.plusSeconds(3600));

        assertThat(maxAge).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void maxAgeResteCorrectJusteAvantLePassageALHeureDHiverEnEurope() {
        // 25 octobre 2026, 00:30 UTC : le passage à l'heure d'hiver en Europe
        // (dernier dimanche d'octobre) a lieu à 01:00 UTC ce jour-là.
        Instant justAvantChangement = Instant.parse("2026-10-25T00:30:00Z");
        Duration maxAge = maxAgeForClockAndExpiration(
                Clock.fixed(justAvantChangement, ZoneId.of("Europe/Zurich")), justAvantChangement.plusSeconds(3600));

        assertThat(maxAge).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void maxAgeNEstJamaisNegatifPourUnTokenValide() {
        Duration maxAge = maxAgeForClockAndExpiration(
                Clock.fixed(NOW_INSTANT, ZoneId.of("Europe/Zurich")), NOW_INSTANT.plusSeconds(3600));

        assertThat(maxAge.isNegative()).isFalse();
    }

    @Test
    void maxAgeResteCoherentAvecLInstantDExpirationDuJwt() {
        Clock clock = Clock.fixed(NOW_INSTANT, ZoneId.of("Europe/Zurich"));
        Instant expiration = NOW_INSTANT.plusSeconds(3600);

        Duration maxAge = maxAgeForClockAndExpiration(clock, expiration);

        assertThat(clock.instant().plus(maxAge)).isEqualTo(expiration);
    }

    @Test
    void rotateCalculeAussiUnMaxAgeIndependantDuFuseauHoraireDuClock() {
        Clock zurichClock = Clock.fixed(NOW_INSTANT, ZoneId.of("Europe/Zurich"));
        RefreshTokenService zurichService =
                new RefreshTokenService(refreshTokenRepository, userInfoRepository, jwtService, zurichClock);
        User user = usableUser();
        String presented = "refresh-token-valide-zurich";
        String hash = sha256Hex(presented);
        RefreshToken stored = storedToken("jti-zurich", hash,
                LocalDateTime.ofInstant(NOW_INSTANT.plus(Duration.ofDays(1)), zurichClock.getZone()), null);

        when(jwtService.extractTokenType(presented)).thenReturn(JwtService.TOKEN_TYPE_REFRESH);
        when(jwtService.extractUsername(presented)).thenReturn(EMAIL);
        when(jwtService.extractJti(presented)).thenReturn("jti-zurich");
        when(refreshTokenRepository.findByJti("jti-zurich")).thenReturn(Optional.of(stored));
        when(userInfoRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(jwtService.generateToken(EMAIL)).thenReturn("nouveau-access-token-zurich");
        when(jwtService.generateRefreshToken(eq(EMAIL), anyString())).thenReturn("nouveau-refresh-token-zurich");
        when(jwtService.extractExpiration("nouveau-refresh-token-zurich"))
                .thenReturn(Date.from(NOW_INSTANT.plusSeconds(3600)));

        RefreshTokenService.IssuedTokens tokens = zurichService.rotate(presented);

        assertThat(tokens.refreshTokenMaxAge()).isEqualTo(Duration.ofHours(1));
    }

    private Duration maxAgeForClockAndExpiration(Clock clock, Instant expiration) {
        RefreshTokenRepository repo = mock(RefreshTokenRepository.class);
        UserInfoRepository userRepo = mock(UserInfoRepository.class);
        JwtService jwt = mock(JwtService.class);
        RefreshTokenService clockedService = new RefreshTokenService(repo, userRepo, jwt, clock);
        User user = usableUser();

        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(jwt.generateToken(EMAIL)).thenReturn("access-token");
        when(jwt.generateRefreshToken(eq(EMAIL), anyString())).thenReturn("refresh-token");
        when(jwt.extractExpiration("refresh-token")).thenReturn(Date.from(expiration));

        return clockedService.issueTokens(EMAIL).refreshTokenMaxAge();
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
