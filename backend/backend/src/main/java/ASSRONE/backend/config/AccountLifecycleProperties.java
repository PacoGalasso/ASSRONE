package ASSRONE.backend.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Typed, validated configuration for the account-lifecycle flows: password
 * reset, email verification, and the scheduled cleanup of their token
 * tables. frontendUrl is what every emailed link is built from — never a
 * hardcoded host — and, like CorsProperties#allowedOrigins, carries no
 * default in production (see application-production.properties): a
 * deployment that forgets to set it fails to start rather than silently
 * emailing links to localhost.
 *
 * Resend/request abuse limits are deliberately not duplicated here: they are
 * owned entirely by RateLimitCategory.PASSWORD_RESET_REQUEST and
 * .EMAIL_VERIFICATION_RESEND (see RateLimiterService), the same mechanism
 * every other public, unauthenticated endpoint in this application already
 * uses — a second, independently-configured limit here would just be two
 * sources of truth for the same policy.
 */
@ConfigurationProperties(prefix = "app.security.account-lifecycle")
@Validated
public record AccountLifecycleProperties(
        @NotBlank String frontendUrl,
        @NotNull @Valid ResetToken resetToken,
        @NotNull @Valid VerificationToken verificationToken,
        @NotNull @Valid Cleanup cleanup) {

    public record ResetToken(
            @NotNull @DefaultValue("1h") Duration expiry) {
    }

    public record VerificationToken(
            @NotNull @DefaultValue("24h") Duration expiry) {
    }

    public record Cleanup(
            @DefaultValue("true") boolean enabled,
            @NotBlank @DefaultValue("0 30 3 * * *") String cron,
            @NotNull @DefaultValue("7d") Duration retentionAfterExpiry) {
    }
}
