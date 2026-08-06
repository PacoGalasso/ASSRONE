package ASSRONE.backend.service;

import ASSRONE.backend.audit.SecurityAuditService;
import ASSRONE.backend.audit.SecurityEventResult;
import ASSRONE.backend.audit.SecurityEventType;
import ASSRONE.backend.exception.InvalidRefreshTokenException;
import ASSRONE.backend.model.RefreshToken;
import ASSRONE.backend.model.User;
import ASSRONE.backend.repository.RefreshTokenRepository;
import ASSRONE.backend.repository.UserInfoRepository;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * Owns the full refresh-token lifecycle: issuance (login), rotation
 * (refresh), and revocation (logout, reuse detection). This is the only
 * place that mints token pairs — JwtService stays pure token mechanics with
 * no database dependency; this class supplies the jti, persists the
 * bookkeeping row, and enforces the security invariants a raw JWT can't
 * enforce on its own (revocability, single-use rotation, freshly-checked
 * account state).
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "Refresh token invalide ou expiré.";

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserInfoRepository userInfoRepository;
    private final JwtService jwtService;
    private final Clock clock;
    private final SecurityAuditService securityAuditService;

    public record IssuedTokens(String accessToken, String refreshToken, String role, String email,
                                Duration refreshTokenMaxAge, Long userId) {
    }

    @Transactional
    public IssuedTokens issueTokens(String email) {
        User user = userInfoRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidRefreshTokenException(INVALID_REFRESH_TOKEN_MESSAGE));
        return mintAndPersist(user);
    }

    @Transactional
    public IssuedTokens rotate(String presentedRefreshToken) {
        String type;
        String email;
        String jti;
        try {
            type = jwtService.extractTokenType(presentedRefreshToken);
            email = jwtService.extractUsername(presentedRefreshToken);
            jti = jwtService.extractJti(presentedRefreshToken);
        } catch (JwtException | IllegalArgumentException ex) {
            recordRefreshDenied(SecurityEventType.REFRESH_DENIED, "-", "MALFORMED_TOKEN");
            throw new InvalidRefreshTokenException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        if (!JwtService.TOKEN_TYPE_REFRESH.equals(type) || jti == null || email == null) {
            recordRefreshDenied(SecurityEventType.REFRESH_DENIED, SecurityAuditService.maskEmail(email),
                    "INVALID_TOKEN_TYPE_OR_CLAIMS");
            throw new InvalidRefreshTokenException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        RefreshToken stored = refreshTokenRepository.findByJti(jti)
                .orElseThrow(() -> {
                    recordRefreshDenied(SecurityEventType.REFRESH_DENIED, SecurityAuditService.maskEmail(email), "UNKNOWN_JTI");
                    return new InvalidRefreshTokenException(INVALID_REFRESH_TOKEN_MESSAGE);
                });

        if (stored.getRevokedAt() != null) {
            // A revoked jti being presented again means either the original
            // refresh already rotated it away, or it was stolen and replayed
            // after the legitimate client rotated. Either way, the whole
            // family of tokens for this user is now suspect: revoke all of
            // them rather than just this one.
            refreshTokenRepository.revokeAllForUser(stored.getUserId(), LocalDateTime.now(clock));
            recordRefreshDenied(SecurityEventType.REFRESH_TOKEN_REUSE_DETECTED,
                    String.valueOf(stored.getUserId()), "REVOKED_TOKEN_REUSED");
            throw new InvalidRefreshTokenException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        if (stored.getExpiresAt().isBefore(LocalDateTime.now(clock))) {
            recordRefreshDenied(SecurityEventType.REFRESH_DENIED, String.valueOf(stored.getUserId()), "EXPIRED");
            throw new InvalidRefreshTokenException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        if (!constantTimeEquals(stored.getTokenHash(), hash(presentedRefreshToken))) {
            // The jti matched but the token's own hash doesn't — never trust
            // a jti match alone to authorize rotation.
            recordRefreshDenied(SecurityEventType.REFRESH_DENIED, String.valueOf(stored.getUserId()), "HASH_MISMATCH");
            throw new InvalidRefreshTokenException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        User user = userInfoRepository.findByEmail(email)
                .orElseThrow(() -> {
                    recordRefreshDenied(SecurityEventType.REFRESH_DENIED, String.valueOf(stored.getUserId()), "USER_NOT_FOUND");
                    return new InvalidRefreshTokenException(INVALID_REFRESH_TOKEN_MESSAGE);
                });
        if (!isUsable(user)) {
            recordRefreshDenied(SecurityEventType.REFRESH_DENIED, String.valueOf(user.getId()), "ACCOUNT_NOT_USABLE");
            throw new InvalidRefreshTokenException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        refreshTokenRepository.revokeByJti(jti, LocalDateTime.now(clock));

        IssuedTokens tokens = mintAndPersist(user);
        securityAuditService.record(SecurityEventType.REFRESH_SUCCESS, SecurityEventResult.SUCCESS,
                String.valueOf(user.getId()), tokens.role(), "refreshToken", null, null);
        return tokens;
    }

    private void recordRefreshDenied(SecurityEventType eventType, String actorId, String reasonCode) {
        securityAuditService.record(eventType, SecurityEventResult.DENIED, actorId, null, "refreshToken", null, reasonCode);
    }

    /**
     * Best-effort and idempotent by design: logout with an absent, already
     * revoked, expired, or malformed token is never an error — there is
     * nothing meaningful left to revoke, and the caller's intent (stop being
     * logged in) is already satisfied locally regardless.
     */
    @Transactional
    public void revoke(String presentedRefreshToken) {
        if (presentedRefreshToken == null || presentedRefreshToken.isBlank()) {
            securityAuditService.record(SecurityEventType.LOGOUT, SecurityEventResult.SUCCESS,
                    "-", null, "refreshToken", null, "NO_SESSION");
            return;
        }
        try {
            String jti = jwtService.extractJti(presentedRefreshToken);
            String email = jwtService.extractUsername(presentedRefreshToken);
            if (jti != null) {
                refreshTokenRepository.revokeByJti(jti, LocalDateTime.now(clock));
            }
            securityAuditService.record(SecurityEventType.LOGOUT, SecurityEventResult.SUCCESS,
                    SecurityAuditService.maskEmail(email), null, "refreshToken", null,
                    jti != null ? "SESSION_REVOKED" : "NO_JTI");
        } catch (JwtException | IllegalArgumentException ignored) {
            // Malformed or already-expired token presented at logout: nothing
            // to revoke, not a failure the caller needs to know about.
            securityAuditService.record(SecurityEventType.LOGOUT, SecurityEventResult.SUCCESS,
                    "-", null, "refreshToken", null, "MALFORMED_TOKEN");
        }
    }

    /**
     * Invalidates every refresh token currently issued to a user, regardless
     * of which device or session it belongs to. Used after a password
     * change: a refresh token stolen before the change must not remain
     * usable afterwards.
     */
    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.revokeAllForUser(userId, LocalDateTime.now(clock));
    }

    private boolean isUsable(User user) {
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            return false;
        }
        LocalDateTime lockedUntil = user.getLockedUntil();
        return lockedUntil == null || lockedUntil.isBefore(LocalDateTime.now(clock));
    }

    private IssuedTokens mintAndPersist(User user) {
        String accessToken = jwtService.generateToken(user.getEmail());
        String jti = UUID.randomUUID().toString();
        String refreshToken = jwtService.generateRefreshToken(user.getEmail(), jti);
        Instant expirationInstant = jwtService.extractExpiration(refreshToken).toInstant();
        // Derived from the injected clock's own zone, never ZoneId.systemDefault():
        // that keeps this value directly comparable to every other
        // LocalDateTime.now(clock) call in this class (the expiresAt check in
        // rotate(), lockedUntil, ...), regardless of what zone the JVM happens
        // to be running in.
        LocalDateTime expiresAt = LocalDateTime.ofInstant(expirationInstant, clock.getZone());

        RefreshToken entity = RefreshToken.builder()
                .userId(user.getId())
                .jti(jti)
                .tokenHash(hash(refreshToken))
                .expiresAt(expiresAt)
                .build();
        refreshTokenRepository.save(entity);

        String role = "ROLE_" + user.getRole().toUpperCase(Locale.ROOT);
        // Computed straight from Instants, never by subtracting two
        // LocalDateTimes: a LocalDateTime carries no zone/offset information,
        // so Duration.between two of them is only correct if both happened to
        // be derived from the exact same zone. Instant arithmetic sidesteps
        // that trap entirely and stays correct across DST transitions and
        // regardless of the JVM's default time zone.
        Duration maxAge = Duration.between(clock.instant(), expirationInstant);
        return new IssuedTokens(accessToken, refreshToken, role, user.getEmail(), maxAge, user.getId());
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponible dans cet environnement.", ex);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
