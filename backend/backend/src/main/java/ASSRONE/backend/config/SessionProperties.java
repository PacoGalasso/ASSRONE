package ASSRONE.backend.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Typed, validated configuration for user session management: how many
 * concurrent sessions a single account may hold, and how the scheduled
 * cleanup of expired/old-revoked sessions behaves.
 *
 * maxActive is bounded (1-20) rather than left open: zero would lock every
 * user out at their very next login, and an unbounded value defeats the
 * point of having a limit at all. retentionAfterExpiry keeps a revoked or
 * expired session's row (and its refresh tokens) around for a while after
 * it stops being usable — see SessionCleanupService — so reuse-detection
 * evidence (RefreshTokenService#rotate) isn't destroyed the moment a
 * session expires.
 */
@ConfigurationProperties(prefix = "app.security.sessions")
@Validated
public record SessionProperties(
        @Min(1) @Max(20) @DefaultValue("5") int maxActive,
        @NotNull @Valid Cleanup cleanup) {

    public record Cleanup(
            @DefaultValue("true") boolean enabled,
            @NotBlank @DefaultValue("0 0 3 * * *") String cron,
            @NotNull @DefaultValue("7d") Duration retentionAfterExpiry) {
    }
}
