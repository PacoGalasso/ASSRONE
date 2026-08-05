package ASSRONE.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Refuses to let the application start under the "production" profile with a
 * manifestly dangerous configuration. CORS emptiness/wildcard is already
 * caught unconditionally, in every profile, by CorsProperties' own Bean
 * Validation; a missing/malformed/wrong-length app.jwt.secret is already
 * caught unconditionally, in every profile, by JwtService#buildSigningKey.
 * What's checked here is specifically wrong only in production:
 *
 * - a refresh cookie without Secure, since production is the only profile
 *   that must be served over HTTPS;
 * - app.jwt.secret equal to the known test signing key committed in this
 *   repository's own test fixtures (BackendApplicationTests,
 *   src/test/resources/application-local.properties) — a value that is
 *   public, passes every structural check above (correct Base64, correct
 *   HS384 length), and must never sign a real token.
 */
@Component
@Profile("production")
public class ProductionSecurityGuard {

    // Intentionally the exact value already public in this repository's test
    // fixtures — rejecting it here is the point, not a new secret being introduced.
    private static final String KNOWN_TEST_JWT_SECRET =
            "zTjvaDrwlDQTMdHQ9vSfqXGwdkGSXJtT09uCOP+KLfO0RmyO617AZi/hK7VKCiKe";

    public ProductionSecurityGuard(RefreshCookieProperties refreshCookieProperties,
                                    @Value("${app.jwt.secret:}") String jwtSecret) {
        if (!refreshCookieProperties.secure()) {
            throw new IllegalStateException(
                    "app.security.refresh-cookie.secure doit valoir true en profil production : "
                            + "un cookie de refresh token non-Secure ne doit jamais être servi en HTTPS. "
                            + "Vérifiez la variable d'environnement REFRESH_COOKIE_SECURE.");
        }
        if (KNOWN_TEST_JWT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException(
                    "app.jwt.secret utilise la clé de test publique de ce dépôt : elle ne doit jamais "
                            + "signer un token en production. Générez un nouveau secret aléatoire "
                            + "(voir .env.example) et positionnez-le via la variable d'environnement JWT_SECRET.");
        }
    }
}
