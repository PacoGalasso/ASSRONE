package ASSRONE.backend.filter;

import ASSRONE.backend.audit.AuditLogCapture;
import ASSRONE.backend.audit.SecurityAuditService;
import ASSRONE.backend.controller.UserController;
import ASSRONE.backend.exception.GlobalExceptionHandler;
import ASSRONE.backend.model.User;
import ASSRONE.backend.repository.UserInfoRepository;
import ASSRONE.backend.security.ClientIpResolver;
import ASSRONE.backend.security.OriginValidator;
import ASSRONE.backend.security.RefreshCookieFactory;
import ASSRONE.backend.service.LoginAttemptService;
import ASSRONE.backend.service.RefreshTokenService;
import ASSRONE.backend.service.UserInfoService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real HTTP requests through AuthCookieOriginFilter and the real UserController
 * (no Testcontainers, no real Spring context) — mirrors the standalone-MockMvc
 * style already used by RateLimitFilterIntegrationTest.
 */
class AuthCookieOriginFilterIntegrationTest {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final List<String> ALLOWED_ORIGINS = List.of("https://assrone.ch", "http://localhost:4200");

    private AuthenticationManager authenticationManager;
    private RefreshTokenService refreshTokenService;
    private UserInfoRepository userInfoRepository;
    private AuthCookieOriginFilter filter;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authenticationManager = mock(AuthenticationManager.class);
        UserInfoService userInfoService = mock(UserInfoService.class);
        userInfoRepository = mock(UserInfoRepository.class);
        refreshTokenService = mock(RefreshTokenService.class);
        LoginAttemptService loginAttemptService = mock(LoginAttemptService.class);
        RefreshCookieFactory refreshCookieFactory = new RefreshCookieFactory(REFRESH_COOKIE_NAME, false, "Lax", "/auth");
        ClientIpResolver clientIpResolver = new ClientIpResolver("");
        SecurityAuditService securityAuditService = new SecurityAuditService(clientIpResolver);
        UserController userController = new UserController(
                userInfoService, userInfoRepository, refreshTokenService, authenticationManager, loginAttemptService,
                refreshCookieFactory, securityAuditService, clientIpResolver);

        filter = new AuthCookieOriginFilter(new OriginValidator(ALLOWED_ORIGINS), securityAuditService);

        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .addFilters(filter)
                .setControllerAdvice(new GlobalExceptionHandler(refreshCookieFactory))
                .build();
    }

    private void stubSuccessfulRotation() {
        when(refreshTokenService.rotate(eq("un-refresh-token"), any())).thenReturn(
                new RefreshTokenService.IssuedTokens("nouveau-access-token", "nouveau-refresh-token",
                        "ROLE_USER", "membre@assrone.ch", Duration.ofDays(7), 1L));
    }

    @Test
    void refreshAvecOrigineAutoriseeFonctionne() throws Exception {
        stubSuccessfulRotation();

        mockMvc.perform(post("/auth/refresh")
                        .header("Origin", "https://assrone.ch")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, "un-refresh-token")))
                .andExpect(status().isOk());
    }

    @Test
    void refreshAvecOrigineEtrangereRecoit403EtNAppelleJamaisLeService() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .header("Origin", "https://evil.com")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, "un-refresh-token")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void refreshAvecOrigineEtrangereJournaliseOriginRejected() throws Exception {
        try (AuditLogCapture capture = new AuditLogCapture()) {
            mockMvc.perform(post("/auth/refresh")
                            .header("Origin", "https://evil.com")
                            .cookie(new Cookie(REFRESH_COOKIE_NAME, "un-refresh-token")))
                    .andExpect(status().isForbidden());

            String line = capture.messages().get(0);
            assertThat(line).contains("eventType=ORIGIN_REJECTED")
                    .contains("result=DENIED")
                    .contains("requestPath=/auth/refresh")
                    .doesNotContain("un-refresh-token");
        }
    }

    @Test
    void refreshAvecMauvaisSchemaRecoit403() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .header("Origin", "http://assrone.ch")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, "un-refresh-token")))
                .andExpect(status().isForbidden());
    }

    @Test
    void refreshAvecMauvaisPortRecoit403() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .header("Origin", "http://localhost:9999")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, "un-refresh-token")))
                .andExpect(status().isForbidden());
    }

    @Test
    void refreshAvecSousDomaineNonAutoriseRecoit403() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .header("Origin", "https://evil.assrone.ch")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, "un-refresh-token")))
                .andExpect(status().isForbidden());
    }

    @Test
    void refreshAvecOrigineNullLitteraleRecoit403() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .header("Origin", "null")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, "un-refresh-token")))
                .andExpect(status().isForbidden());
    }

    @Test
    void refreshAvecOrigineMalformeeRecoit403() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .header("Origin", "pas une origine")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, "un-refresh-token")))
                .andExpect(status().isForbidden());
    }

    @Test
    void refreshAvecRefererAutoriseSansOrigineFonctionne() throws Exception {
        stubSuccessfulRotation();

        mockMvc.perform(post("/auth/refresh")
                        .header("Referer", "https://assrone.ch/profile")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, "un-refresh-token")))
                .andExpect(status().isOk());
    }

    @Test
    void refreshAvecRefererEtrangerSansOrigineRecoit403() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .header("Referer", "https://evil.com/page")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, "un-refresh-token")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void refreshSansOrigineNiRefererRecoit403() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, "un-refresh-token")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void logoutAvecOrigineAutoriseeFonctionne() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .header("Origin", "https://assrone.ch")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, "un-refresh-token")))
                .andExpect(status().isNoContent());
    }

    @Test
    void logoutAvecOrigineEtrangereRecoit403EtNeRevoqueJamaisLeToken() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .header("Origin", "https://evil.com")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, "un-refresh-token")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void requetePrefligthOptionsSurRefreshNestJamaisBloquee() throws Exception {
        // The filter only ever gates POST (see isProtected); OPTIONS preflights — even
        // from a disallowed Origin, which is normal for a CORS preflight — must always
        // reach the rest of the chain untouched. Exercised directly against the filter
        // (bypassing MockMvc's dispatcher, which has no preflight handler adapter wired
        // in this standalone setup) since only the filter's own routing decision is
        // under test here, not Spring's CORS preflight response construction.
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/auth/refresh");
        request.addHeader("Origin", "https://evil.com");
        request.addHeader("Access-Control-Request-Method", "POST");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void loginNestJamaisSoumisAuMecanismeCookieCsrfMemeAvecUneOrigineEtrangere() throws Exception {
        UsernamePasswordAuthenticationToken authentifie = new UsernamePasswordAuthenticationToken(
                "membre@assrone.ch", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(authenticationManager.authenticate(any())).thenReturn(authentifie);
        User verifie = new User();
        verifie.setId(1L);
        verifie.setEmail("membre@assrone.ch");
        verifie.setEmailVerifiedAt(LocalDateTime.now());
        when(userInfoRepository.findByEmail("membre@assrone.ch")).thenReturn(Optional.of(verifie));
        when(refreshTokenService.issueTokens(any(), any(), any())).thenReturn(
                new RefreshTokenService.IssuedTokens("access-token", "refresh-token", "ROLE_USER", "membre@assrone.ch",
                        Duration.ofDays(7), 1L));

        mockMvc.perform(post("/auth/generateToken")
                        .header("Origin", "https://evil.com")
                        .contentType("application/json")
                        .content("""
                                {"email":"membre@assrone.ch","password":"bon-mot-de-passe"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void enTeteXForwardedHostFalsifieNInfluenceJamaisLaDecision() throws Exception {
        // AuthCookieOriginFilter never reads X-Forwarded-*: only the client-asserted
        // Origin/Referer are compared against the configured allow-list, so a spoofed
        // forwarded header cannot be used to bypass or trigger the rejection.
        mockMvc.perform(post("/auth/refresh")
                        .header("Origin", "https://evil.com")
                        .header("X-Forwarded-Host", "assrone.ch")
                        .header("X-Forwarded-Proto", "https")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, "un-refresh-token")))
                .andExpect(status().isForbidden());
    }
}
