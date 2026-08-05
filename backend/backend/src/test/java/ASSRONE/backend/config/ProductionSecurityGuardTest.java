package ASSRONE.backend.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecurityGuardTest {

    @Test
    void refuseDeDemarrerSiLeCookieDeRefreshNEstPasSecure() {
        RefreshCookieProperties insecure = new RefreshCookieProperties("refresh_token", false, "Lax", "/auth");

        assertThatThrownBy(() -> new ProductionSecurityGuard(insecure))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secure");
    }

    @Test
    void demarreNormalementSiLeCookieDeRefreshEstSecure() {
        RefreshCookieProperties secure = new RefreshCookieProperties("refresh_token", true, "Lax", "/auth");

        assertThatCode(() -> new ProductionSecurityGuard(secure)).doesNotThrowAnyException();
    }
}
