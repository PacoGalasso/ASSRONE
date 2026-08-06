package ASSRONE.backend.dto;

import java.time.LocalDateTime;

/**
 * What a user is allowed to see about one of their own sessions. Built
 * exclusively from UserSession — never from RefreshToken — so there is no
 * field here that could even accidentally expose a jti, a token hash, a
 * cookie value, or the internal (sequential) session ID.
 */
public record SessionDto(
        String id,
        boolean current,
        LocalDateTime createdAt,
        LocalDateTime lastUsedAt,
        LocalDateTime expiresAt,
        String ipAddress,
        String device) {
}
