package ASSRONE.backend.controller;

import ASSRONE.backend.exception.GlobalExceptionHandler;
import ASSRONE.backend.exception.SessionNotFoundException;
import ASSRONE.backend.model.User;
import ASSRONE.backend.model.UserSession;
import ASSRONE.backend.repository.UserInfoRepository;
import ASSRONE.backend.security.RefreshCookieFactory;
import ASSRONE.backend.service.JwtService;
import ASSRONE.backend.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SessionControllerTest {

    private static final String EMAIL = "membre@assrone.ch";
    private static final String CURRENT_SID = "current-session-public-id";

    private SessionService sessionService;
    private UserInfoRepository userInfoRepository;
    private JwtService jwtService;
    private MockMvc mockMvc;
    private final Authentication principal = new UsernamePasswordAuthenticationToken(EMAIL, null);

    @BeforeEach
    void setUp() {
        sessionService = mock(SessionService.class);
        userInfoRepository = mock(UserInfoRepository.class);
        jwtService = mock(JwtService.class);
        RefreshCookieFactory refreshCookieFactory = new RefreshCookieFactory("refresh_token", false, "Lax", "/auth");

        when(userInfoRepository.findByEmail(EMAIL)).thenReturn(Optional.of(
                User.builder().id(1L).email(EMAIL).username("membre").password("hash").role("USER").isActive(true).build()));

        SessionController controller = new SessionController(sessionService, userInfoRepository, jwtService, refreshCookieFactory);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(refreshCookieFactory))
                .build();
    }

    private static UserSession session(Long id, String publicId) {
        return UserSession.builder()
                .id(id)
                .publicId(publicId)
                .userId(1L)
                .createdAt(LocalDateTime.of(2026, 8, 1, 10, 0))
                .lastUsedAt(LocalDateTime.of(2026, 8, 5, 10, 0))
                .expiresAt(LocalDateTime.of(2026, 8, 12, 10, 0))
                .lastSeenIp("203.0.113.1")
                .userAgentLabel("Mozilla/5.0")
                .build();
    }

    @Test
    void listSessionsRetourneLesSessionsAvecLaSessionCouranteMarquee() throws Exception {
        when(jwtService.extractSessionId("access-token")).thenReturn(CURRENT_SID);
        when(sessionService.listActiveSessions(1L)).thenReturn(List.of(session(1L, CURRENT_SID), session(2L, "other-sid")));

        mockMvc.perform(get("/api/me/sessions").header("Authorization", "Bearer access-token").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions.length()").value(2))
                .andExpect(jsonPath("$.sessions[0].id").value(CURRENT_SID))
                .andExpect(jsonPath("$.sessions[0].current").value(true))
                .andExpect(jsonPath("$.sessions[1].id").value("other-sid"))
                .andExpect(jsonPath("$.sessions[1].current").value(false));
    }

    @Test
    void listSessionsNeContientAucunChampTechniqueSensible() throws Exception {
        when(sessionService.listActiveSessions(1L)).thenReturn(List.of(session(1L, CURRENT_SID)));

        mockMvc.perform(get("/api/me/sessions").header("Authorization", "Bearer access-token").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[0].jti").doesNotExist())
                .andExpect(jsonPath("$.sessions[0].tokenHash").doesNotExist())
                .andExpect(jsonPath("$.sessions[0].refreshToken").doesNotExist())
                .andExpect(jsonPath("$.sessions[0].userId").doesNotExist());
    }

    @Test
    void listSessionsSansTokenAuthorizationNAffichePasDeSessionCourante() throws Exception {
        when(sessionService.listActiveSessions(1L)).thenReturn(List.of(session(1L, CURRENT_SID)));

        mockMvc.perform(get("/api/me/sessions").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[0].current").value(false));
    }

    @Test
    void revokeOneRevoqueLaSessionDemandeeEtRetourne204() throws Exception {
        stubCurrentSessionLookup();
        when(sessionService.revokeOwnSession(eq(1L), eq("target-sid"), eq(1L), eq("1")))
                .thenReturn(new SessionService.RevocationOutcome(true, false));

        mockMvc.perform(delete("/api/me/sessions/{id}", "target-sid")
                        .header("Authorization", "Bearer access-token").principal(principal))
                .andExpect(status().isNoContent());

        verify(sessionService).revokeOwnSession(eq(1L), eq("target-sid"), eq(1L), eq("1"));
    }

    @Test
    void revokeOneDeLaSessionCouranteVideAussiLeCookieDeRafraichissement() throws Exception {
        stubCurrentSessionLookup();
        when(sessionService.revokeOwnSession(eq(1L), eq(CURRENT_SID), eq(1L), eq("1")))
                .thenReturn(new SessionService.RevocationOutcome(true, true));

        mockMvc.perform(delete("/api/me/sessions/{id}", CURRENT_SID)
                        .header("Authorization", "Bearer access-token").principal(principal))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh_token", 0));
    }

    @Test
    void revokeOneDuneAutreSessionNeTouchePasAuCookie() throws Exception {
        stubCurrentSessionLookup();
        when(sessionService.revokeOwnSession(eq(1L), eq("target-sid"), eq(1L), eq("1")))
                .thenReturn(new SessionService.RevocationOutcome(true, false));

        mockMvc.perform(delete("/api/me/sessions/{id}", "target-sid")
                        .header("Authorization", "Bearer access-token").principal(principal))
                .andExpect(status().isNoContent())
                .andExpect(cookie().doesNotExist("refresh_token"));
    }

    @Test
    void revokeOneSessionInconnueRetourne404Generique() throws Exception {
        stubCurrentSessionLookup();
        when(sessionService.revokeOwnSession(eq(1L), eq("inconnu"), any(), any()))
                .thenThrow(new SessionNotFoundException("Session introuvable."));

        mockMvc.perform(delete("/api/me/sessions/{id}", "inconnu")
                        .header("Authorization", "Bearer access-token").principal(principal))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Session introuvable."));
    }

    @Test
    void revokeOthersRetourneLeNombreDeSessionsRevoquees() throws Exception {
        stubCurrentSessionLookup();
        when(sessionService.revokeOthers(eq(1L), eq(1L), eq("1"))).thenReturn(3);

        mockMvc.perform(delete("/api/me/sessions/others")
                        .header("Authorization", "Bearer access-token").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revokedCount").value(3));
    }

    @Test
    void revokeAllRevoqueToutEtViseLeCookieDeRafraichissement() throws Exception {
        when(sessionService.revokeAll(eq(1L), eq("1"))).thenReturn(5);

        mockMvc.perform(delete("/api/me/sessions").principal(principal))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh_token", 0));

        verify(sessionService).revokeAll(eq(1L), eq("1"));
    }

    @Test
    void unUtilisateurNeVoitJamaisLesSessionsDunAutreUtilisateur() throws Exception {
        when(sessionService.listActiveSessions(1L)).thenReturn(List.of(session(1L, CURRENT_SID)));

        mockMvc.perform(get("/api/me/sessions").principal(principal))
                .andExpect(status().isOk());

        verify(sessionService, never()).listActiveSessions(eq(2L));
    }

    private void stubCurrentSessionLookup() {
        when(jwtService.extractSessionId("access-token")).thenReturn(CURRENT_SID);
        when(sessionService.listActiveSessions(1L)).thenReturn(List.of(session(1L, CURRENT_SID)));
    }
}
