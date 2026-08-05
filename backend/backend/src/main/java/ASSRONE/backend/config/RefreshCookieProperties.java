package ASSRONE.backend.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Typed, validated view of the refresh-cookie properties also read directly
 * (via {@code @Value}) by RefreshCookieFactory. This class exists to make
 * the raw property values fail fast and loudly at context startup if they
 * are structurally invalid (blank name/path, unrecognized SameSite value),
 * and to give ProductionSecurityGuard a typed value to check {@code secure}
 * against — it intentionally does not replace RefreshCookieFactory's own
 * {@code @Value} wiring, to avoid touching the refresh-cookie issuance code
 * path in a lot scoped to configuration hardening only.
 *
 * Each {@code @DefaultValue} mirrors RefreshCookieFactory's own {@code @Value}
 * fallback so this binds identically whether or not a given property is
 * present — it only ever applies when a key is entirely absent from every
 * property source.
 */
@ConfigurationProperties(prefix = "app.security.refresh-cookie")
@Validated
public record RefreshCookieProperties(
        @NotBlank @DefaultValue("refresh_token") String name,
        @DefaultValue("false") boolean secure,
        @NotBlank @Pattern(regexp = "Strict|Lax|None", message = "doit valoir Strict, Lax ou None")
        @DefaultValue("Lax") String sameSite,
        @NotBlank @DefaultValue("/auth") String path) {
}
