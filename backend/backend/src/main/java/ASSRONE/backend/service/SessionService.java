package ASSRONE.backend.service;

import ASSRONE.backend.audit.SecurityAuditService;
import ASSRONE.backend.audit.SecurityEventResult;
import ASSRONE.backend.audit.SecurityEventType;
import ASSRONE.backend.config.SessionProperties;
import ASSRONE.backend.exception.InvalidSessionIdException;
import ASSRONE.backend.exception.SessionNotFoundException;
import ASSRONE.backend.model.UserSession;
import ASSRONE.backend.repository.RefreshTokenRepository;
import ASSRONE.backend.repository.UserSessionRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Owns the UserSession lifecycle: creation (with the concurrent-safe
 * session-limit enforcement), listing, and every revocation path (single
 * session, "other" sessions, all sessions, reuse-detection fallout, and the
 * scheduled cleanup of old rows). RefreshTokenService calls into this class
 * to create/touch a session on login/refresh; nothing here calls back into
 * RefreshTokenService, so there is no circular dependency between the two —
 * revocation reaches into RefreshTokenRepository directly.
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    // A single fixed advisory-lock namespace shared by every user, combined
    // with the user's own id as the second key of the two-argument overload:
    // this serializes session-creation transactions for the SAME user
    // (closing the same "read count, then insert" race AdminUserManagementService
    // closes for the admin count) without serializing unrelated users against
    // each other, which a single fixed-key lock (as used there) would do.
    private static final int SESSION_LOCK_NAMESPACE = 91_000;

    private static final int USER_AGENT_LABEL_MAX_LENGTH = 255;

    private final UserSessionRepository userSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecurityAuditService securityAuditService;
    private final SessionProperties sessionProperties;
    private final EntityManager entityManager;
    private final Clock clock;

    public record RevocationOutcome(boolean revoked, boolean wasCurrentSession) {
    }

    public record CleanupResult(int revokedSessionsDeleted, int expiredSessionsDeleted) {
    }

    @Transactional
    public UserSession createSession(Long userId, LocalDateTime expiresAt, String clientIp, String rawUserAgent) {
        LocalDateTime now = LocalDateTime.now(clock);
        acquireSessionCreationLock(userId);

        long activeCount = userSessionRepository.countByUserIdAndRevokedAtIsNullAndExpiresAtAfter(userId, now);
        if (activeCount >= sessionProperties.maxActive()) {
            userSessionRepository
                    .findFirstByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastUsedAtAsc(userId, now)
                    .ifPresent(oldest -> {
                        revokeSessionAndTokens(oldest.getId(), now, "SESSION_LIMIT_ENFORCED");
                        securityAuditService.record(SecurityEventType.SESSION_LIMIT_ENFORCED, SecurityEventResult.SUCCESS,
                                String.valueOf(userId), null, "session", oldest.getPublicId(), "OLDEST_SESSION_REVOKED");
                    });
        }

        UserSession session = UserSession.builder()
                .publicId(UUID.randomUUID().toString())
                .userId(userId)
                .createdAt(now)
                .lastUsedAt(now)
                .expiresAt(expiresAt)
                .createdIp(clientIp)
                .lastSeenIp(clientIp)
                .userAgentLabel(sanitizeUserAgent(rawUserAgent))
                .build();
        return userSessionRepository.save(session);
    }

    @Transactional
    public UserSession touchSession(Long sessionId, LocalDateTime expiresAt, String clientIp) {
        LocalDateTime now = LocalDateTime.now(clock);
        userSessionRepository.touch(sessionId, now, expiresAt, clientIp);
        return userSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalStateException("Session " + sessionId + " introuvable lors de la rotation."));
    }

    public List<UserSession> listActiveSessions(Long userId) {
        return userSessionRepository.findAllByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastUsedAtDesc(
                userId, LocalDateTime.now(clock));
    }

    /**
     * Whether the session identified by {@code sessionId} is itself revoked
     * — used by RefreshTokenService#rotate to tell an explicit, expected
     * revocation (logout, "revoke this session", limit enforcement...) apart
     * from genuine token-reuse (a superseded-by-rotation jti presented
     * again), which must NOT also be treated as reuse.
     */
    public boolean isSessionRevoked(Long sessionId) {
        return userSessionRepository.findById(sessionId)
                .map(session -> session.getRevokedAt() != null)
                .orElse(true);
    }

    /** State-only revocation with no audit emission — the caller already logs its own event (LOGOUT, reuse detection). */
    @Transactional
    public void revokeSessionSilently(Long sessionId, String reason) {
        revokeSessionAndTokens(sessionId, LocalDateTime.now(clock), reason);
    }

    /** Same as {@link #revokeSessionSilently(Long, String)} but for every active session of a user at once. */
    @Transactional
    public void revokeAllSessionsSilently(Long userId, String reason) {
        LocalDateTime now = LocalDateTime.now(clock);
        userSessionRepository.revokeAllForUser(userId, now, reason);
        refreshTokenRepository.revokeAllForUser(userId, now);
    }

    @Transactional
    public RevocationOutcome revokeOwnSession(Long userId, String publicId, Long currentSessionId, String actorId) {
        UserSession session;
        try {
            session = resolveOwnedSession(userId, publicId);
        } catch (SessionNotFoundException | InvalidSessionIdException ex) {
            securityAuditService.record(SecurityEventType.SESSION_REVOCATION_DENIED, SecurityEventResult.DENIED,
                    actorId, null, "session", null,
                    ex instanceof InvalidSessionIdException ? "MALFORMED_ID" : "NOT_FOUND_OR_NOT_OWNED");
            throw ex;
        }
        boolean isCurrent = session.getId().equals(currentSessionId);

        revokeSessionAndTokens(session.getId(), LocalDateTime.now(clock), "SESSION_REVOKED");
        securityAuditService.record(SecurityEventType.SESSION_REVOKED, SecurityEventResult.SUCCESS,
                actorId, null, "session", session.getPublicId(), isCurrent ? "CURRENT" : "OTHER");
        return new RevocationOutcome(true, isCurrent);
    }

    @Transactional
    public int revokeOthers(Long userId, Long currentSessionId, String actorId) {
        LocalDateTime now = LocalDateTime.now(clock);
        int revoked = userSessionRepository.revokeAllForUserExcept(userId, currentSessionId, now, "OTHER_SESSIONS_REVOKED");
        // The current session's own still-open refresh token is deliberately
        // excluded (WHERE sessionId <> keepSessionId), so the caller
        // performing this action is never logged out by it — harmless to run
        // even when revoked == 0.
        refreshTokenRepository.revokeAllForUserExceptSession(userId, currentSessionId, now);
        securityAuditService.record(SecurityEventType.OTHER_SESSIONS_REVOKED, SecurityEventResult.SUCCESS,
                actorId, null, "session", null, String.valueOf(revoked));
        return revoked;
    }

    @Transactional
    public int revokeAll(Long userId, String actorId) {
        LocalDateTime now = LocalDateTime.now(clock);
        int revoked = userSessionRepository.revokeAllForUser(userId, now, "ALL_SESSIONS_REVOKED");
        refreshTokenRepository.revokeAllForUser(userId, now);
        securityAuditService.record(SecurityEventType.ALL_SESSIONS_REVOKED, SecurityEventResult.SUCCESS,
                actorId, null, "session", null, String.valueOf(revoked));
        return revoked;
    }

    @Transactional
    public CleanupResult cleanup(java.time.Duration retentionAfterExpiry) {
        LocalDateTime before = LocalDateTime.now(clock).minus(retentionAfterExpiry);
        int revokedDeleted = userSessionRepository.deleteRevokedBefore(before);
        int expiredDeleted = userSessionRepository.deleteExpiredAndNeverRevokedBefore(before);
        if (revokedDeleted > 0 || expiredDeleted > 0) {
            securityAuditService.record(SecurityEventType.SESSION_CLEANUP_COMPLETED, SecurityEventResult.SUCCESS,
                    "-", null, "session", null,
                    "revoked=" + revokedDeleted + ";expired=" + expiredDeleted);
        }
        return new CleanupResult(revokedDeleted, expiredDeleted);
    }

    private UserSession resolveOwnedSession(Long userId, String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw new InvalidSessionIdException("Identifiant de session invalide.");
        }
        UserSession session = userSessionRepository.findByPublicId(publicId)
                .orElseThrow(() -> new SessionNotFoundException("Session introuvable."));
        if (!session.getUserId().equals(userId)) {
            // Deliberately the exact same exception/message as "not found":
            // an owner-scoped lookup must never let a caller distinguish
            // "doesn't exist" from "belongs to someone else".
            throw new SessionNotFoundException("Session introuvable.");
        }
        return session;
    }

    private int revokeSessionAndTokens(Long sessionId, LocalDateTime now, String reason) {
        int affected = userSessionRepository.revokeById(sessionId, now, reason);
        refreshTokenRepository.revokeAllForSession(sessionId, now);
        return affected;
    }

    private void acquireSessionCreationLock(Long userId) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:ns, :uid)")
                .setParameter("ns", SESSION_LOCK_NAMESPACE)
                .setParameter("uid", userId.intValue())
                .getSingleResult();
    }

    private static String sanitizeUserAgent(String rawUserAgent) {
        if (rawUserAgent == null || rawUserAgent.isBlank()) {
            return null;
        }
        String stripped = rawUserAgent.replaceAll("[\\p{Cntrl}]", "").trim();
        if (stripped.isEmpty()) {
            return null;
        }
        return stripped.length() > USER_AGENT_LABEL_MAX_LENGTH
                ? stripped.substring(0, USER_AGENT_LABEL_MAX_LENGTH)
                : stripped;
    }
}
