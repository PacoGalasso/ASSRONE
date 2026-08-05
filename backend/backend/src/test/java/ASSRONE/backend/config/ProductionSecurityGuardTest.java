package ASSRONE.backend.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecurityGuardTest {

    private static final String KNOWN_TEST_JWT_SECRET =
            "zTjvaDrwlDQTMdHQ9vSfqXGwdkGSXJtT09uCOP+KLfO0RmyO617AZi/hK7VKCiKe";
    private static final String UN_AUTRE_SECRET = "un-secret-de-production-different-de-celui-des-tests";

    @Test
    void refuseDeDemarrerSiLeCookieDeRefreshNEstPasSecure() {
        RefreshCookieProperties insecure = new RefreshCookieProperties("refresh_token", false, "Lax", "/auth");

        assertThatThrownBy(() -> new ProductionSecurityGuard(insecure, UN_AUTRE_SECRET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secure");
    }

    @Test
    void demarreNormalementSiLeCookieDeRefreshEstSecureEtLeSecretDistinct() {
        RefreshCookieProperties secure = new RefreshCookieProperties("refresh_token", true, "Lax", "/auth");

        assertThatCode(() -> new ProductionSecurityGuard(secure, UN_AUTRE_SECRET)).doesNotThrowAnyException();
    }

    @Test
    void refuseDeDemarrerSiLeSecretJwtEstLaValeurDeTestConnue() {
        RefreshCookieProperties secure = new RefreshCookieProperties("refresh_token", true, "Lax", "/auth");

        assertThatThrownBy(() -> new ProductionSecurityGuard(secure, KNOWN_TEST_JWT_SECRET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.jwt.secret");
    }
}
