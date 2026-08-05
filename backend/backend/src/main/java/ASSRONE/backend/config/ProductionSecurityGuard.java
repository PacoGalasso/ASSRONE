package ASSRONE.backend.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Refuses to let the application start under the "production" profile with
 * a manifestly dangerous refresh-cookie configuration. CORS emptiness/
 * wildcard is already caught unconditionally, in every profile, by
 * CorsProperties' own Bean Validation — the one thing that is only wrong
 * specifically in production (and is fine, even expected, in local/test) is
 * a refresh cookie without Secure, since production is the only profile
 * that must be served over HTTPS.
 */
@Component
@Profile("production")
public class ProductionSecurityGuard {

    public ProductionSecurityGuard(RefreshCookieProperties refreshCookieProperties) {
        if (!refreshCookieProperties.secure()) {
            throw new IllegalStateException(
                    "app.security.refresh-cookie.secure doit valoir true en profil production : "
                            + "un cookie de refresh token non-Secure ne doit jamais être servi en HTTPS. "
                            + "Vérifiez la variable d'environnement REFRESH_COOKIE_SECURE.");
        }
    }
}
