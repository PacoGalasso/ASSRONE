package ASSRONE.backend.config;

import ASSRONE.backend.security.ContentSecurityPolicy;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * deployment/nginx/assrone.conf.example documents the reverse-proxy config a real
 * deployment would need for the security headers this lot and the CSP lot added —
 * see that file's own header comment for why the proxy, not Spring, is meant to own
 * these specifically for the static frontend. This test reads the actual file and
 * asserts its header values are byte-for-byte identical to what SecurityConfig
 * actually sends, so the two can never silently drift apart.
 */
class NginxExampleHeadersConsistencyTest {

    // backend/backend/ -> repo root -> deployment/nginx/...
    private static final Path NGINX_EXAMPLE =
            Path.of("..", "..", "deployment", "nginx", "assrone.conf.example");

    private String readExampleConfig() throws IOException {
        assertThat(Files.exists(NGINX_EXAMPLE))
                .as("deployment/nginx/assrone.conf.example doit exister à : %s", NGINX_EXAMPLE.toAbsolutePath())
                .isTrue();
        return Files.readString(NGINX_EXAMPLE);
    }

    @Test
    void contientLaPolitiqueCspDeProductionAIdentique() throws IOException {
        String config = readExampleConfig();

        assertThat(config).contains(ContentSecurityPolicy.directives(true));
    }

    @Test
    void contientLaPermissionsPolicyIdentique() throws IOException {
        String config = readExampleConfig();

        assertThat(config).contains(SecurityConfig.PERMISSIONS_POLICY);
    }

    @Test
    void contientTousLesHeadersDeSecuriteRequis() throws IOException {
        String config = readExampleConfig();

        assertThat(config).contains("Content-Security-Policy");
        assertThat(config).contains("X-Content-Type-Options");
        assertThat(config).contains("nosniff");
        assertThat(config).contains("X-Frame-Options");
        assertThat(config).contains("DENY");
        assertThat(config).contains("Referrer-Policy");
        assertThat(config).contains("strict-origin-when-cross-origin");
        assertThat(config).contains("Permissions-Policy");
        assertThat(config).contains("Cross-Origin-Opener-Policy");
        assertThat(config).contains("same-origin");
        assertThat(config).contains("Cross-Origin-Resource-Policy");
    }

    @Test
    void neDupliqueLesHeadersQueSurLaFrontendPasSurLesLocationsProxifiees() throws IOException {
        String config = readExampleConfig();

        // The add_header block sits after the /api/, /auth/ and /health proxy_pass
        // locations (they already get these headers from Spring — see the file's own
        // comment) and before the static-asset locations. A crude but effective check:
        // "add_header Content-Security-Policy" must appear exactly once — Spring owns
        // it for the proxied API/auth locations, this file owns it only once, for the
        // static frontend.
        assertThat(config.split("add_header Content-Security-Policy", -1)).hasSize(2);
    }

    @Test
    void neContientAucunDomaineNiCheminDeCertificatReel() throws IOException {
        String config = readExampleConfig();

        assertThat(config).doesNotContain("assrone.ch");
        assertThat(config).contains("ASSRONE_DOMAIN");
        assertThat(config).contains("ASSRONE_BACKEND_UPSTREAM");
    }
}
