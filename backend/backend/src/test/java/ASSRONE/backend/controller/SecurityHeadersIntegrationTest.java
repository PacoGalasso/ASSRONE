package ASSRONE.backend.controller;

import ASSRONE.backend.audit.SecurityAuditService;
import ASSRONE.backend.config.JwtAuthenticationEntryPoint;
import ASSRONE.backend.config.RateLimitConfig;
import ASSRONE.backend.config.SecurityConfig;
import ASSRONE.backend.dto.EventDto;
import ASSRONE.backend.filter.AuthCookieOriginFilter;
import ASSRONE.backend.filter.CorrelationIdFilter;
import ASSRONE.backend.filter.JwtAuthFilter;
import ASSRONE.backend.filter.RateLimitFilter;
import ASSRONE.backend.ratelimit.RateLimiterService;
import ASSRONE.backend.security.ClientIpResolver;
import ASSRONE.backend.security.OriginValidator;
import ASSRONE.backend.security.RefreshCookieFactory;
import ASSRONE.backend.service.EventService;
import ASSRONE.backend.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the security headers configured in SecurityConfig (CSP, Referrer-Policy,
 * Permissions-Policy, COOP, CORP, plus Spring Security's own X-Content-Type-Options
 * and X-Frame-Options defaults) are present, correct, and never duplicated — on
 * public, protected, admin-only, and error (401/403) responses alike, since they
 * come from a single chain-wide header writer rather than per-controller code.
 * Exercised through EventController only: the header writer applies identically to
 * every response regardless of which controller produced it, so repeating this
 * across every controller would test the same mechanism again without adding
 * confidence (see CommitteeMemberController/ProfileController's inline-rendered
 * photo/avatar endpoints, and DocumentController's downloads, which all go through
 * this same chain).
 */
@WebMvcTest(controllers = EventController.class)
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
class SecurityHeadersIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@assrone.ch";
    private static final String USER_EMAIL = "membre@assrone.ch";
    private static final String TOKEN = "token-de-test";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private EventService eventService;

    private void authenticateAs(String email, String role) {
        UserDetails principal = mock(UserDetails.class);
        when(principal.getUsername()).thenReturn(email);
        when(principal.isEnabled()).thenReturn(true);
        when(principal.isAccountNonLocked()).thenReturn(true);
        org.mockito.Mockito.doReturn(List.of(new SimpleGrantedAuthority("ROLE_" + role)))
                .when(principal).getAuthorities();
        when(jwtService.extractTokenType(TOKEN)).thenReturn(JwtService.TOKEN_TYPE_ACCESS);
        when(jwtService.extractUsername(TOKEN)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(principal);
        when(jwtService.validateToken(TOKEN, principal)).thenReturn(true);
    }

    private static final String EXPECTED_CSP = "default-src 'self'; script-src 'self'; script-src-attr 'none'; "
            + "style-src 'self'; img-src 'self' blob:; font-src 'self' https://fonts.gstatic.com; "
            + "connect-src 'self'; frame-src 'none'; frame-ancestors 'none'; object-src 'none'; "
            + "base-uri 'self'; form-action 'self'";

    private void assertSecurityHeaders(MvcResult result) {
        var response = result.getResponse();

        assertThat(response.getHeaders("Content-Security-Policy")).containsExactly(EXPECTED_CSP);
        assertThat(response.getHeaders("X-Content-Type-Options")).containsExactly("nosniff");
        assertThat(response.getHeaders("X-Frame-Options")).containsExactly("DENY");
        assertThat(response.getHeaders("Referrer-Policy")).containsExactly("strict-origin-when-cross-origin");
        assertThat(response.getHeaders("Permissions-Policy")).hasSize(1);
        assertThat(response.getHeader("Permissions-Policy")).contains("camera=()", "geolocation=()", "microphone=()");
        assertThat(response.getHeaders("Cross-Origin-Opener-Policy")).containsExactly("same-origin");
        assertThat(response.getHeaders("Cross-Origin-Resource-Policy")).containsExactly("same-origin");
    }

    @Test
    void reponsePubliqueContientTousLesHeadersDeSecuriteSansDoublon() throws Exception {
        when(eventService.getUpcomingEvents()).thenReturn(List.of());

        MvcResult result = mockMvc.perform(get("/api/events/upcoming"))
                .andExpect(status().isOk())
                .andReturn();

        assertSecurityHeaders(result);
    }

    @Test
    void reponseProtegeeContientTousLesHeadersDeSecurite() throws Exception {
        authenticateAs(ADMIN_EMAIL, "ADMIN");
        when(eventService.updateEvent(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(EventDto.builder().id(1L).title("Atelier").type("Conférence").build());

        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/events/1")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType("application/json")
                        .content("""
                                {"title":"Atelier","description":"Description","type":"Conférence",
                                 "eventDate":"2027-01-01","startTime":"18:00","endTime":"20:00",
                                 "location":"Local associatif","maxParticipants":10}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        assertSecurityHeaders(result);
    }

    @Test
    void erreur401ContientTousLesHeadersDeSecurite() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/events")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertSecurityHeaders(result);
    }

    @Test
    void erreur403ContientTousLesHeadersDeSecurite() throws Exception {
        authenticateAs(USER_EMAIL, "USER");

        MvcResult result = mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType("application/json")
                        .content("""
                                {"title":"Atelier","description":"Description","type":"Conférence",
                                 "eventDate":"2027-01-01","startTime":"18:00","endTime":"20:00",
                                 "location":"Local associatif","maxParticipants":10}
                                """))
                .andExpect(status().isForbidden())
                .andReturn();

        assertSecurityHeaders(result);
    }

    @Test
    void aucunHeaderDeSecuriteNestDuplique() throws Exception {
        when(eventService.getUpcomingEvents()).thenReturn(List.of());

        MvcResult result = mockMvc.perform(get("/api/events/upcoming"))
                .andExpect(status().isOk())
                .andReturn();

        for (String name : List.of("Content-Security-Policy", "X-Content-Type-Options", "X-Frame-Options",
                "Referrer-Policy", "Permissions-Policy", "Cross-Origin-Opener-Policy", "Cross-Origin-Resource-Policy")) {
            assertThat(result.getResponse().getHeaders(name)).as("header " + name).hasSize(1);
        }
    }

    @Test
    void headerXContentTypeOptionsEstToujoursNosniff() throws Exception {
        when(eventService.getUpcomingEvents()).thenReturn(List.of());

        mockMvc.perform(get("/api/events/upcoming"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void reponse429DeRateLimitFilterConserveAussiLesHeadersDeSecurite() throws Exception {
        when(eventService.register(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(EventDto.builder().id(1L).build());
        String body = """
                {"fullName":"Jean Dupont","email":"jean.dupont@example.com"}
                """;

        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/api/events/1/register").contentType("application/json").content(body));
        }
        MvcResult result = mockMvc.perform(post("/api/events/1/register").contentType("application/json").content(body))
                .andExpect(status().isTooManyRequests())
                .andReturn();

        assertSecurityHeaders(result);
    }
}
