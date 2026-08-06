package ASSRONE.backend.filter;

import ASSRONE.backend.audit.SecurityAuditService;
import ASSRONE.backend.controller.ContactController;
import ASSRONE.backend.controller.EventController;
import ASSRONE.backend.controller.MembershipApplicationController;
import ASSRONE.backend.controller.UserController;
import ASSRONE.backend.dto.EventDto;
import ASSRONE.backend.dto.MembershipApplicationDto;
import ASSRONE.backend.exception.GlobalExceptionHandler;
import ASSRONE.backend.ratelimit.RateLimiterService;
import ASSRONE.backend.security.ClientIpResolver;
import ASSRONE.backend.security.RefreshCookieFactory;
import ASSRONE.backend.service.ContactEmailService;
import ASSRONE.backend.service.EventService;
import ASSRONE.backend.service.LoginAttemptService;
import ASSRONE.backend.service.MembershipApplicationService;
import ASSRONE.backend.service.RefreshTokenService;
import ASSRONE.backend.service.UserInfoService;
import io.github.bucket4j.TimeMeter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real HTTP requests through RateLimitFilter and standalone controllers (no
 * Testcontainers, no real Spring context) — all downstream services are mocked
 * so no email is ever sent and no database is touched.
 */
class RateLimitFilterIntegrationTest {

    private static final String LOGIN_BODY = """
            {"email":"membre@assrone.ch","password":"mauvais-mot-de-passe"}
            """;

    private AuthenticationManager authenticationManager;
    private ContactEmailService contactEmailService;
    private MembershipApplicationService membershipApplicationService;
    private EventService eventService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authenticationManager = mock(AuthenticationManager.class);
        UserInfoService userInfoService = mock(UserInfoService.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        when(refreshTokenService.issueTokens(any(), any(), any())).thenReturn(
                new RefreshTokenService.IssuedTokens("access-token", "refresh-token", "ROLE_USER", "membre@assrone.ch",
                        Duration.ofDays(7), 1L));
        LoginAttemptService loginAttemptService = mock(LoginAttemptService.class);
        contactEmailService = mock(ContactEmailService.class);
        membershipApplicationService = mock(MembershipApplicationService.class);
        eventService = mock(EventService.class);

        ClientIpResolver clientIpResolver = new ClientIpResolver("");
        RateLimiterService rateLimiterService = new RateLimiterService(
                TimeMeter.SYSTEM_NANOTIME, com.github.benmanes.caffeine.cache.Ticker.systemTicker());
        RateLimitFilter rateLimitFilter = new RateLimitFilter(rateLimiterService, clientIpResolver,
                new SecurityAuditService(clientIpResolver));

        RefreshCookieFactory refreshCookieFactory = new RefreshCookieFactory("refresh_token", false, "Lax", "/auth");
        UserController userController = new UserController(userInfoService, refreshTokenService, authenticationManager,
                loginAttemptService, refreshCookieFactory, new SecurityAuditService(clientIpResolver), clientIpResolver);
        ContactController contactController = new ContactController(contactEmailService);
        MembershipApplicationController membershipApplicationController =
                new MembershipApplicationController(membershipApplicationService);
        EventController eventController = new EventController(eventService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(userController, contactController, membershipApplicationController, eventController)
                .addFilters(rateLimitFilter)
                .setControllerAdvice(new GlobalExceptionHandler(refreshCookieFactory))
                .build();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withIp(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder, String ip) {
        return builder.with(request -> {
            request.setRemoteAddr(ip);
            return request;
        });
    }

    @Test
    void dixLoginsAutorisesOnzemeBloqueDepuisLaMemeIp() throws Exception {
        UsernamePasswordAuthenticationToken authentifie = new UsernamePasswordAuthenticationToken(
                "membre@assrone.ch", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(authenticationManager.authenticate(any())).thenReturn(authentifie);

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(withIp(post("/auth/generateToken"), "20.0.0.1")
                            .contentType("application/json")
                            .content(LOGIN_BODY))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(withIp(post("/auth/generateToken"), "20.0.0.1")
                        .contentType("application/json")
                        .content(LOGIN_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().json("{\"error\":\"Trop de requêtes. Veuillez réessayer plus tard.\"}"))
                .andExpect(header().exists("Retry-After"));

        verify(authenticationManager, org.mockito.Mockito.times(10)).authenticate(any());
    }

    @Test
    void requeteBloqueeNAtteintJamaisLAuthenticationManager() throws Exception {
        UsernamePasswordAuthenticationToken authentifie = new UsernamePasswordAuthenticationToken(
                "membre@assrone.ch", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(authenticationManager.authenticate(any())).thenReturn(authentifie);

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(withIp(post("/auth/generateToken"), "20.0.0.2")
                    .contentType("application/json")
                    .content(LOGIN_BODY));
        }
        org.mockito.Mockito.clearInvocations(authenticationManager);

        mockMvc.perform(withIp(post("/auth/generateToken"), "20.0.0.2")
                        .contentType("application/json")
                        .content(LOGIN_BODY))
                .andExpect(status().isTooManyRequests());

        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void contactEstLimiteIndependammentDuLogin() throws Exception {
        UsernamePasswordAuthenticationToken authentifie = new UsernamePasswordAuthenticationToken(
                "membre@assrone.ch", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(authenticationManager.authenticate(any())).thenReturn(authentifie);
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(withIp(post("/auth/generateToken"), "20.0.0.3")
                    .contentType("application/json")
                    .content(LOGIN_BODY));
        }
        mockMvc.perform(withIp(post("/auth/generateToken"), "20.0.0.3")
                        .contentType("application/json")
                        .content(LOGIN_BODY))
                .andExpect(status().isTooManyRequests());

        String contactBody = """
                {"fullName":"Jean Dupont","email":"jean@example.com","subject":"Bonjour","message":"Test"}
                """;
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(withIp(post("/api/contact"), "20.0.0.3")
                            .contentType("application/json")
                            .content(contactBody))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(withIp(post("/api/contact"), "20.0.0.3")
                        .contentType("application/json")
                        .content(contactBody))
                .andExpect(status().isTooManyRequests());

        verify(contactEmailService, org.mockito.Mockito.times(5)).sendContactMessage(any());
    }

    @Test
    void membershipApplicationEstLimiteSeparement() throws Exception {
        when(membershipApplicationService.submit(any())).thenReturn(MembershipApplicationDto.builder().build());
        String body = """
                {"fullName":"Jean Dupont","email":"jean@example.com","membershipType":"INDIVIDUEL","charterAccepted":true}
                """;

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(withIp(post("/api/membership-applications"), "20.0.0.4")
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(withIp(post("/api/membership-applications"), "20.0.0.4")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void inscriptionEvenementEstLimiteeSeparementEtMatchePartial() throws Exception {
        when(eventService.register(org.mockito.ArgumentMatchers.eq(42L), any())).thenReturn(EventDto.builder().build());
        String body = """
                {"fullName":"Jean Dupont","email":"jean@example.com"}
                """;

        for (int i = 0; i < 20; i++) {
            mockMvc.perform(withIp(post("/api/events/42/register"), "20.0.0.5")
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(withIp(post("/api/events/42/register"), "20.0.0.5")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void deuxIpSepareesOntChacuneLeurPropreLimite() throws Exception {
        UsernamePasswordAuthenticationToken authentifie = new UsernamePasswordAuthenticationToken(
                "membre@assrone.ch", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(authenticationManager.authenticate(any())).thenReturn(authentifie);

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(withIp(post("/auth/generateToken"), "20.0.0.6")
                    .contentType("application/json")
                    .content(LOGIN_BODY));
        }
        mockMvc.perform(withIp(post("/auth/generateToken"), "20.0.0.6")
                        .contentType("application/json")
                        .content(LOGIN_BODY))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(withIp(post("/auth/generateToken"), "20.0.0.7")
                        .contentType("application/json")
                        .content(LOGIN_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void getPublicsNeSontJamaisLimites() throws Exception {
        when(eventService.getUpcomingEvents()).thenReturn(List.of());

        for (int i = 0; i < 30; i++) {
            mockMvc.perform(withIp(get("/api/events/upcoming"), "20.0.0.8"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void endpointNonListePasAffecteParLeFiltre() throws Exception {
        when(eventService.createEvent(any())).thenReturn(EventDto.builder().build());
        String body = """
                {"title":"Atelier","description":"Description","type":"ATELIER","eventDate":"2027-01-01",
                 "startTime":"18:00","endTime":"20:00","location":"Salle A","maxParticipants":10}
                """;

        // Not in RateLimitedRoutes.ROUTES: never blocked regardless of volume,
        // unlike /api/events/*/register which shares the same controller.
        for (int i = 0; i < 11; i++) {
            mockMvc.perform(withIp(post("/api/events"), "20.0.0.9")
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isCreated());
        }
    }

    private MockMvc mockMvcWithTrustedProxies(String trustedProxiesCsv) {
        ClientIpResolver clientIpResolver = new ClientIpResolver(trustedProxiesCsv);
        RateLimiterService rateLimiterService = new RateLimiterService(
                TimeMeter.SYSTEM_NANOTIME, com.github.benmanes.caffeine.cache.Ticker.systemTicker());
        RateLimitFilter rateLimitFilter = new RateLimitFilter(rateLimiterService, clientIpResolver,
                new SecurityAuditService(clientIpResolver));

        UserInfoService userInfoService = mock(UserInfoService.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        when(refreshTokenService.issueTokens(any(), any(), any())).thenReturn(
                new RefreshTokenService.IssuedTokens("access-token", "refresh-token", "ROLE_USER", "membre@assrone.ch",
                        Duration.ofDays(7), 1L));
        LoginAttemptService loginAttemptService = mock(LoginAttemptService.class);
        RefreshCookieFactory refreshCookieFactory = new RefreshCookieFactory("refresh_token", false, "Lax", "/auth");
        UserController userController = new UserController(userInfoService, refreshTokenService, authenticationManager,
                loginAttemptService, refreshCookieFactory, new SecurityAuditService(clientIpResolver), clientIpResolver);

        return MockMvcBuilders
                .standaloneSetup(userController)
                .addFilters(rateLimitFilter)
                .setControllerAdvice(new GlobalExceptionHandler(refreshCookieFactory))
                .build();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withIpAndForwardedFor(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder, String remoteAddr, String forwardedFor) {
        return builder.with(request -> {
            request.setRemoteAddr(remoteAddr);
            return request;
        }).header("X-Forwarded-For", forwardedFor);
    }

    @Test
    void deuxClientsDerriereLeMemeProxyDeConfianceOntChacunLeurPropreLimite() throws Exception {
        UsernamePasswordAuthenticationToken authentifie = new UsernamePasswordAuthenticationToken(
                "membre@assrone.ch", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(authenticationManager.authenticate(any())).thenReturn(authentifie);

        MockMvc trustedMockMvc = mockMvcWithTrustedProxies("50.0.0.1");

        for (int i = 0; i < 10; i++) {
            trustedMockMvc.perform(withIpAndForwardedFor(post("/auth/generateToken"), "50.0.0.1", "60.0.0.1")
                            .contentType("application/json")
                            .content(LOGIN_BODY))
                    .andExpect(status().isOk());
        }
        trustedMockMvc.perform(withIpAndForwardedFor(post("/auth/generateToken"), "50.0.0.1", "60.0.0.1")
                        .contentType("application/json")
                        .content(LOGIN_BODY))
                .andExpect(status().isTooManyRequests());

        // Second client relayed by the same trusted proxy, different forwarded IP:
        // independent bucket, not affected by the first client's exhausted quota.
        trustedMockMvc.perform(withIpAndForwardedFor(post("/auth/generateToken"), "50.0.0.1", "60.0.0.2")
                        .contentType("application/json")
                        .content(LOGIN_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void spoofingDepuisUnClientDirectNonApprouveNeContournePasLaLimite() throws Exception {
        UsernamePasswordAuthenticationToken authentifie = new UsernamePasswordAuthenticationToken(
                "membre@assrone.ch", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(authenticationManager.authenticate(any())).thenReturn(authentifie);

        MockMvc trustedMockMvc = mockMvcWithTrustedProxies("50.0.0.1");

        // Direct client at 70.0.0.1 is not a trusted proxy: a different forwarded IP on
        // every request must not let it dodge the limiter, since the header is ignored.
        for (int i = 0; i < 10; i++) {
            trustedMockMvc.perform(withIpAndForwardedFor(post("/auth/generateToken"), "70.0.0.1", "1.2.3." + i)
                            .contentType("application/json")
                            .content(LOGIN_BODY))
                    .andExpect(status().isOk());
        }
        trustedMockMvc.perform(withIpAndForwardedFor(post("/auth/generateToken"), "70.0.0.1", "9.9.9.9")
                        .contentType("application/json")
                        .content(LOGIN_BODY))
                .andExpect(status().isTooManyRequests());
    }
}
