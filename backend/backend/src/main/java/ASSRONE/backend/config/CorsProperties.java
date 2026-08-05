package ASSRONE.backend.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Typed, validated view of app.security.cors.allowed-origins, also read
 * directly (via {@code @Value}) by SecurityConfig#corsConfigurationSource.
 * Binding this class makes the application refuse to start, in every
 * profile, with an empty origin list or a "*" wildcard — the latter can
 * never legitimately work here anyway, since SecurityConfig always sets
 * allowCredentials(true), and browsers refuse allowCredentials together
 * with a wildcard origin.
 *
 * The {@code @DefaultValue} mirrors SecurityConfig's own {@code @Value}
 * fallback so this binds identically whether or not the property is present
 * — it only ever applies when the key is entirely absent from every
 * property source; application-production.properties leaves the property
 * unresolvable-if-unset instead, which fails at placeholder resolution
 * before binding is ever reached.
 */
@ConfigurationProperties(prefix = "app.security.cors")
@Validated
public record CorsProperties(
        @NotEmpty @DefaultValue("http://localhost:4200") List<@NotBlank String> allowedOrigins) {

    @AssertTrue(message = "app.security.cors.allowed-origins ne doit jamais contenir \"*\" : "
            + "un wildcard est incompatible avec allowCredentials(true).")
    public boolean isFreeOfWildcard() {
        return allowedOrigins == null || allowedOrigins.stream().noneMatch("*"::equals);
    }
}
