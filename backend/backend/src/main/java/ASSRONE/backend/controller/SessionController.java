package ASSRONE.backend.controller;

import ASSRONE.backend.dto.SessionDto;
import ASSRONE.backend.dto.SessionListResponse;
import ASSRONE.backend.dto.SessionsRevokedResponse;
import ASSRONE.backend.exception.InvalidSessionIdException;
import ASSRONE.backend.model.User;
import ASSRONE.backend.model.UserSession;
import ASSRONE.backend.repository.UserInfoRepository;
import ASSRONE.backend.security.RefreshCookieFactory;
import ASSRONE.backend.service.JwtService;
import ASSRONE.backend.service.SessionService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Lets a user see and end their own login sessions. Deliberately Bearer-only
 * (like every other /api/** mutation route) rather than relying on the
 * refresh cookie, so these stay naturally outside AuthCookieOriginFilter's
 * scope and add no CSRF exposure. Only "revoke all" additionally clears the
 * refresh cookie, since that operation also ends the caller's own session.
 */
@RestController
@RequestMapping("/api/me/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final UserInfoRepository userInfoRepository;
    private final JwtService jwtService;
    private final RefreshCookieFactory refreshCookieFactory;

    @GetMapping
    public SessionListResponse listSessions(Authentication authentication, HttpServletRequest request) {
        User user = currentUser(authentication);
        String currentPublicId = currentSessionPublicId(request);

        List<SessionDto> sessions = sessionService.listActiveSessions(user.getId())
                .stream()
                .map(session -> toDto(session, currentPublicId))
                .toList();
        return new SessionListResponse(sessions);
    }

    @DeleteMapping("/others")
    public SessionsRevokedResponse revokeOthers(Authentication authentication, HttpServletRequest request) {
        User user = currentUser(authentication);
        Long currentSessionId = requireCurrentSessionId(user, request);

        int revoked = sessionService.revokeOthers(user.getId(), currentSessionId, String.valueOf(user.getId()));
        return new SessionsRevokedResponse(revoked);
    }

    @DeleteMapping
    public ResponseEntity<Void> revokeAll(Authentication authentication, HttpServletResponse response) {
        User user = currentUser(authentication);
        sessionService.revokeAll(user.getId(), String.valueOf(user.getId()));
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookieFactory.clear().toString());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> revokeOne(@PathVariable String sessionId, Authentication authentication,
                                           HttpServletRequest request, HttpServletResponse response) {
        User user = currentUser(authentication);
        Long currentSessionId = resolveCurrentSessionId(user, request);

        SessionService.RevocationOutcome outcome = sessionService.revokeOwnSession(
                user.getId(), sessionId, currentSessionId, String.valueOf(user.getId()));
        // Revoking your own current session ends it exactly like /auth/logout
        // does: the refresh cookie backing it must go too, or the browser
        // would still try (and fail) to refresh with it moments later.
        if (outcome.wasCurrentSession()) {
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookieFactory.clear().toString());
        }
        return ResponseEntity.noContent().build();
    }

    private SessionDto toDto(UserSession session, String currentPublicId) {
        return new SessionDto(
                session.getPublicId(),
                session.getPublicId().equals(currentPublicId),
                session.getCreatedAt(),
                session.getLastUsedAt(),
                session.getExpiresAt(),
                session.getLastSeenIp(),
                session.getUserAgentLabel());
    }

    private User currentUser(Authentication authentication) {
        return userInfoRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Utilisateur authentifié introuvable : " + authentication.getName()));
    }

    /**
     * Never null for a legitimately-authenticated request: every access
     * token minted since this lot carries "sid". Only historical access
     * tokens issued before this change (still valid until their own 30-
     * minute expiry) could lack it, in which case "revoke others" cannot
     * determine which session to keep and is refused outright rather than
     * guessing — accepted as a one-off transitional edge case bounded by
     * the access-token lifetime, never a silent wrong answer.
     */
    private Long requireCurrentSessionId(User user, HttpServletRequest request) {
        Long id = resolveCurrentSessionId(user, request);
        if (id == null) {
            throw new InvalidSessionIdException(
                    "Session actuelle introuvable ; reconnectez-vous puis réessayez.");
        }
        return id;
    }

    private Long resolveCurrentSessionId(User user, HttpServletRequest request) {
        String publicId = currentSessionPublicId(request);
        if (publicId == null) {
            return null;
        }
        return sessionService.listActiveSessions(user.getId()).stream()
                .filter(session -> session.getPublicId().equals(publicId))
                .map(UserSession::getId)
                .findFirst()
                .orElse(null);
    }

    private String currentSessionPublicId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        try {
            return jwtService.extractSessionId(header.substring(7));
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }
}
