package ASSRONE.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A logical login session, stable across every refresh-token rotation that
 * belongs to it. RefreshToken rows come and go as a session rotates; this
 * row is what a user actually sees and can revoke in "Sessions actives".
 */
@Entity
@Table(name = "user_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_used_at", nullable = false)
    private LocalDateTime lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revocation_reason", length = 64)
    private String revocationReason;

    @Column(name = "created_ip", length = 45)
    private String createdIp;

    @Column(name = "last_seen_ip", length = 45)
    private String lastSeenIp;

    @Column(name = "user_agent_label")
    private String userAgentLabel;
}
