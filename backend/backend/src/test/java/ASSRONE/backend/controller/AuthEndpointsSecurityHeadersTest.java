package ASSRONE.backend.controller;

import ASSRONE.backend.audit.SecurityAuditService;
import ASSRONE.backend.config.JwtAuthenticationEntryPoint;
import ASSRONE.backend.config.RateLimitConfig;
import ASSRONE.backend.config.SecurityConfig;
import ASSRONE.backend.filter.AuthCookieOriginFilter;
import ASSRONE.backend.filter.CorrelationIdFilter;
import ASSRONE.backend.filter.JwtAuthFilter;
import ASSRONE.backend.filter.RateLimitFilter;
import ASSRONE.backend.ratelimit.RateLimiterService;
import ASSRONE.backend.repository.UserInfoRepository;
import ASSRONE.backend.security.ClientIpResolver;
import ASSRONE.backend.security.OriginValidator;
import ASSRONE.backend.security.RefreshCookieFactory;
import ASSRONE.backend.service.JwtService;
import ASSRONE.backend.service.LoginAttemptService;
import ASSRONE.backend.service.RefreshTokenService;
import ASSRONE.backend.service.UserInfoService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms that /auth/refresh and /auth/logout — the two endpoints hardened by the
 * previous lot's AuthCookieOriginFilter — carry the same chain-wide security headers
 * as every other response, on both their success path AND on AuthCookieOriginFilter's
 * 403 rejection path, and that adding the headers configuration did not regress the
 * origin-validation behavior from that lot.
 */
@WebMvcTest(controllers = UserController.class)
@Import({
        SecurityConfig.class,
        JwtAuthFilter.class,
        JwtAuthenticationEntryPoint.class,
        RateLimitFilter.class,
        RateLimiterService.class,
        RateLimitConfig.class,
        ClientIpResolver.class,
        RefreshCookieFactory.class,
        AuthCookieOriginFilter.class,
        OriginValidator.class,
        CorrelationIdFilter.class,
        SecurityAuditService.class
})
class AuthEndpointsSecurityHeadersTest {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final String ALLOWED_ORIGIN = "http://localhost:4200";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserInfoService userInfoService;
    @MockitoBean
    private UserInfoRepository userInfoRepository;
    @MockitoBean
    private AuthenticationManager authenticationManager;
    @MockitoBean
    private RefreshTokenService refreshTokenService;
    @MockitoBean
    private LoginAttemptService loginAttemptService;
    // Not mocked separately: UserInfoService already implements UserDetailsService
    // (see UserController's own dependency below), and JwtAuthFilter needs exactly
    // one UserDetailsService bean to resolve unambiguously.
    @MockitoBean
    private JwtService jwtService;

    private void assertSecurityHeaders(MvcResult result) {
        var response = result.getResponse();

        assertThat(response.getHeaders("Content-Security-Policy")).hasSize(1);
        assertThat(response.getHeaders("X-Content-Type-Options")).containsExactly("nosniff");
        assertThat(response.getHeaders("X-Frame-Options")).containsExactly("DENY");
        assertThat(response.getHeaders("Referrer-Policy")).containsExactly("strict-origin-when-cross-origin");
        assertThat(response.getHeaders("Permissions-Policy")).hasSize(1);
        assertThat(response.getHeaders("Cross-Origin-Opener-Policy")).containsExactly("same-origin");
        assertThat(response.getHeaders("Cross-Origin-Resource-Policy")).containsExactly("same-origin");
    }

    @Test
    void refreshReussiConserveLaValidationDOrigineEtLesHeadersDeSecurite() throws Exception {
        when(refreshTokenService.rotate(org.mockito.ArgumentMatchers.eq("un-refresh-token"), org.mockito.ArgumentMatchers.any())).thenReturn(
                new RefreshTokenService.IssuedTokens("nouveau-access-token", "nouveau-refresh-token",
                        "ROLE_USER", "membre@assrone.ch", Duration.ofDays(7), 1L));

        MvcResult result = mockMvc.perform(post("/auth/refresh")
                        .header("Origin", ALLOWED_ORIGIN)
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, "un-refresh-token")))
                .andExpect(status().isOk())
                .andReturn();

        assertSecurityHeaders(result);
    }

    @Test
    void refreshRejeteParOrigineEtrangereConserveAussiLesHeadersDeSecurite() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/refresh")
                        .header("Origin", "https://evil.com")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, "un-refresh-token")))
                .andExpect(status().isForbidden())
                .andReturn();

        assertSecurityHeaders(result);
    }

    @Test
    void logoutReussiConserveLaValidationDOrigineEtLesHeadersDeSecurite() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/logout")
                        .header("Origin", ALLOWED_ORIGIN)
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, "un-refresh-token")))
                .andExpect(status().isNoContent())
                .andReturn();

        assertSecurityHeaders(result);
    }

    @Test
    void logoutRejeteParOrigineEtrangereConserveAussiLesHeadersDeSecurite() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/logout")
                        .header("Origin", "https://evil.com")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, "un-refresh-token")))
                .andExpect(status().isForbidden())
                .andReturn();

        assertSecurityHeaders(result);
    }
}
