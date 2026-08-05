package ASSRONE.backend.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentSecurityPolicyTest {

    @Test
    void politiqueLocaleNeContientPasUpgradeInsecureRequests() {
        assertThat(ContentSecurityPolicy.directives(false)).doesNotContain("upgrade-insecure-requests");
    }

    @Test
    void politiqueProductionContientUpgradeInsecureRequests() {
        assertThat(ContentSecurityPolicy.directives(true)).contains("upgrade-insecure-requests");
    }

    @Test
    void aucuneDirectiveNeContientDeWildcard() {
        assertThat(ContentSecurityPolicy.directives(true)).doesNotContain("*");
    }

    @Test
    void scriptSrcNeContientNiUnsafeInlineNiUnsafeEval() {
        String policy = ContentSecurityPolicy.directives(true);

        assertThat(policy).contains("script-src 'self'");
        assertThat(policy).doesNotContain("unsafe-inline");
        assertThat(policy).doesNotContain("unsafe-eval");
    }

    @Test
    void frameAncestorsEstCoherentAvecXFrameOptionsDeny() {
        assertThat(ContentSecurityPolicy.directives(false)).contains("frame-ancestors 'none'");
    }

    @Test
    void objectSrcEstNone() {
        assertThat(ContentSecurityPolicy.directives(false)).contains("object-src 'none'");
    }

    @Test
    void baseUriEstRestreintALOrigine() {
        assertThat(ContentSecurityPolicy.directives(false)).contains("base-uri 'self'");
    }

    @Test
    void formActionEstRestreintALOrigine() {
        assertThat(ContentSecurityPolicy.directives(false)).contains("form-action 'self'");
    }
}
