package ASSRONE.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // nullable=false here matches the real Postgres constraint exactly (V7
    // backfills every existing row before adding it): safe precisely because
    // Hibernate never runs any schema DDL against this table before that
    // constraint already exists — see LegacyBaselineFlywayCallback and
    // application.properties (ddl-auto=validate, unconditional, every
    // environment) for the ordering guarantee that makes this true.
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(nullable = false, unique = true, length = 36)
    private String jti;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
