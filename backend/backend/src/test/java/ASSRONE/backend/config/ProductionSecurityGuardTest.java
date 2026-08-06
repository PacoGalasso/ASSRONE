package ASSRONE.backend.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecurityGuardTest {

    private static final String KNOWN_TEST_JWT_SECRET =
            "zTjvaDrwlDQTMdHQ9vSfqXGwdkGSXJtT09uCOP+KLfO0RmyO617AZi/hK7VKCiKe";
    private static final String UN_AUTRE_SECRET = "un-secret-de-production-different-de-celui-des-tests";
    private static final String SMTP_HOST = "smtp.assrone.ch";
    private static final String SMTP_PASSWORD = "un-mot-de-passe-smtp";

    @Test
    void refuseDeDemarrerSiLeCookieDeRefreshNEstPasSecure() {
        RefreshCookieProperties insecure = new RefreshCookieProperties("refresh_token", false, "Lax", "/auth");

        assertThatThrownBy(() -> new ProductionSecurityGuard(insecure, UN_AUTRE_SECRET, SMTP_HOST, SMTP_PASSWORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secure");
    }

    @Test
    void demarreNormalementSiLeCookieDeRefreshEstSecureEtLeSecretDistinct() {
        RefreshCookieProperties secure = new RefreshCookieProperties("refresh_token", true, "Lax", "/auth");

        assertThatCode(() -> new ProductionSecurityGuard(secure, UN_AUTRE_SECRET, SMTP_HOST, SMTP_PASSWORD))
                .doesNotThrowAnyException();
    }

    @Test
    void refuseDeDemarrerSiLeSecretJwtEstLaValeurDeTestConnue() {
        RefreshCookieProperties secure = new RefreshCookieProperties("refresh_token", true, "Lax", "/auth");

        assertThatThrownBy(() -> new ProductionSecurityGuard(secure, KNOWN_TEST_JWT_SECRET, SMTP_HOST, SMTP_PASSWORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.jwt.secret");
    }

    @Test
    void refuseDeDemarrerSiLHoteSmtpEstVide() {
        RefreshCookieProperties secure = new RefreshCookieProperties("refresh_token", true, "Lax", "/auth");

        assertThatThrownBy(() -> new ProductionSecurityGuard(secure, UN_AUTRE_SECRET, "", SMTP_PASSWORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.mail.host");
    }

    @Test
    void refuseDeDemarrerSiLeMotDePasseSmtpEstVide() {
        RefreshCookieProperties secure = new RefreshCookieProperties("refresh_token", true, "Lax", "/auth");

        assertThatThrownBy(() -> new ProductionSecurityGuard(secure, UN_AUTRE_SECRET, SMTP_HOST, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.mail.password");
    }

    @Test
    void refuseDeDemarrerSiLeMotDePasseSmtpNestQueDesEspaces() {
        RefreshCookieProperties secure = new RefreshCookieProperties("refresh_token", true, "Lax", "/auth");

        assertThatThrownBy(() -> new ProductionSecurityGuard(secure, UN_AUTRE_SECRET, SMTP_HOST, "   "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.mail.password");
    }
}
