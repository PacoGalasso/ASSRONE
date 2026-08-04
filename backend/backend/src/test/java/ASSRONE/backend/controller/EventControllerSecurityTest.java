package ASSRONE.backend.controller;

import ASSRONE.backend.config.JwtAuthenticationEntryPoint;
import ASSRONE.backend.config.RateLimitConfig;
import ASSRONE.backend.config.SecurityConfig;
import ASSRONE.backend.dto.EventDto;
import ASSRONE.backend.exception.EventCapacityConflictException;
import ASSRONE.backend.exception.ResourceNotFoundException;
import ASSRONE.backend.filter.JwtAuthFilter;
import ASSRONE.backend.filter.RateLimitFilter;
import ASSRONE.backend.ratelimit.RateLimiterService;
import ASSRONE.backend.security.ClientIpResolver;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EventController.class)
@Import({
        SecurityConfig.class,
        JwtAuthFilter.class,
        JwtAuthenticationEntryPoint.class,
        RateLimitFilter.class,
        RateLimiterService.class,
        RateLimitConfig.class,
        ClientIpResolver.class
})
class EventControllerSecurityTest {

    private static final String ADMIN_EMAIL = "admin@assrone.ch";
    private static final String USER_EMAIL = "membre@assrone.ch";
    private static final String TOKEN = "token-de-test";
    private static final String VALID_BODY = """
            {"title":"Atelier bénévolat","description":"Description","type":"Conférence",
             "eventDate":"2027-01-01","startTime":"18:00","endTime":"20:00",
             "location":"Local associatif","maxParticipants":10}
            """;

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
        org.mockito.Mockito.doReturn(List.of(new SimpleGrantedAuthority("ROLE_" + role)))
                .when(principal).getAuthorities();
        when(jwtService.extractUsername(TOKEN)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(principal);
        when(jwtService.validateToken(TOKEN, principal)).thenReturn(true);
    }

    @Test
    void requeteSansAuthentificationRecoit401() throws Exception {
        mockMvc.perform(put("/api/events/1")
                        .contentType("application/json")
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void utilisateurNonAdministrateurRecoit403() throws Exception {
        authenticateAs(USER_EMAIL, "USER");

        mockMvc.perform(put("/api/events/1")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType("application/json")
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void payloadInvalideRecoit400() throws Exception {
        authenticateAs(ADMIN_EMAIL, "ADMIN");
        String invalidBody = """
                {"title":"","description":"Description","type":"Conférence",
                 "eventDate":"2027-01-01","startTime":"18:00","endTime":"20:00",
                 "location":"Local associatif","maxParticipants":10}
                """;

        mockMvc.perform(put("/api/events/1")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType("application/json")
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void typeVideRecoit400() throws Exception {
        authenticateAs(ADMIN_EMAIL, "ADMIN");
        String invalidBody = """
                {"title":"Atelier","description":"Description","type":"   ",
                 "eventDate":"2027-01-01","startTime":"18:00","endTime":"20:00",
                 "location":"Local associatif","maxParticipants":10}
                """;

        mockMvc.perform(put("/api/events/1")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType("application/json")
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void evenementInexistantRecoit404() throws Exception {
        authenticateAs(ADMIN_EMAIL, "ADMIN");
        doThrow(new ResourceNotFoundException("Événement introuvable : 99"))
                .when(eventService).updateEvent(eq(99L), org.mockito.ArgumentMatchers.any());

        mockMvc.perform(put("/api/events/99")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType("application/json")
                        .content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void capaciteInferieureAuxInscritsRecoit409() throws Exception {
        authenticateAs(ADMIN_EMAIL, "ADMIN");
        doThrow(new EventCapacityConflictException("La capacité demandée est inférieure au nombre d'inscrits actuels."))
                .when(eventService).updateEvent(eq(1L), org.mockito.ArgumentMatchers.any());

        mockMvc.perform(put("/api/events/1")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType("application/json")
                        .content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void administrateurModifieUnEvenementAvecSucces() throws Exception {
        authenticateAs(ADMIN_EMAIL, "ADMIN");
        when(eventService.updateEvent(eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(EventDto.builder().id(1L).title("Atelier bénévolat").type("Conférence").build());

        mockMvc.perform(put("/api/events/1")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType("application/json")
                        .content(VALID_BODY))
                .andExpect(status().isOk());

        verify(eventService).updateEvent(eq(1L), org.mockito.ArgumentMatchers.any());
    }
}
