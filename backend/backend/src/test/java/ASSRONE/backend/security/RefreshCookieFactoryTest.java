package ASSRONE.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshCookieFactoryTest {

    @Test
    void issueProduitUnCookieHttpOnlyAvecLesAttributsConfigures() {
        RefreshCookieFactory factory = new RefreshCookieFactory("refresh_token", true, "Strict", "/auth");

        ResponseCookie cookie = factory.issue("un-refresh-token", Duration.ofDays(7));

        assertThat(cookie.getName()).isEqualTo("refresh_token");
        assertThat(cookie.getValue()).isEqualTo("un-refresh-token");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/auth");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void secureRefleteLaConfigurationMemeQuandFaux() {
        RefreshCookieFactory factory = new RefreshCookieFactory("refresh_token", false, "Lax", "/auth");

        ResponseCookie cookie = factory.issue("un-refresh-token", Duration.ofDays(7));

        assertThat(cookie.isSecure()).isFalse();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
    }

    @Test
    void clearProduitUnCookieVideAvecMaxAgeZeroEtLesMemesAttributsQueIssue() {
        RefreshCookieFactory factory = new RefreshCookieFactory("refresh_token", true, "Lax", "/auth");

        ResponseCookie cleared = factory.clear();

        assertThat(cleared.getName()).isEqualTo("refresh_token");
        assertThat(cleared.getValue()).isEmpty();
        assertThat(cleared.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(cleared.isHttpOnly()).isTrue();
        assertThat(cleared.isSecure()).isTrue();
        assertThat(cleared.getPath()).isEqualTo("/auth");
    }

    @Test
    void getCookieNameExposeLeNomConfigure() {
        RefreshCookieFactory factory = new RefreshCookieFactory("mon_cookie", false, "Lax", "/auth");

        assertThat(factory.getCookieName()).isEqualTo("mon_cookie");
    }
}
