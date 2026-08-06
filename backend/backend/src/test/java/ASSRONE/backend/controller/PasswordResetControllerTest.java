package ASSRONE.backend.controller;

import ASSRONE.backend.exception.GlobalExceptionHandler;
import ASSRONE.backend.exception.InvalidPasswordResetTokenException;
import ASSRONE.backend.ratelimit.RateLimiterService;
import ASSRONE.backend.security.RefreshCookieFactory;
import ASSRONE.backend.service.PasswordResetService;
import io.github.bucket4j.ConsumptionProbe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The controller is deliberately thin — see PasswordResetController's own
 * javadoc — so these tests focus on exactly what belongs at this layer: the
 * response for forgotPassword never varies with what the service actually
 * did, rate limiting is enforced before the service is ever called, and
 * error bodies never leak more than the generic message.
 */
class PasswordResetControllerTest {

    private PasswordResetService passwordResetService;
    private RateLimiterService rateLimiterService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        passwordResetService = mock(PasswordResetService.class);
        rateLimiterService = mock(RateLimiterService.class);
        ConsumptionProbe consumed = mock(ConsumptionProbe.class);
        when(consumed.isConsumed()).thenReturn(true);
        when(rateLimiterService.tryConsume(anyString(), any())).thenReturn(consumed);

        PasswordResetController controller = new PasswordResetController(passwordResetService, rateLimiterService);
        RefreshCookieFactory refreshCookieFactory = new RefreshCookieFactory("refresh_token", false, "Lax", "/auth");
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(refreshCookieFactory))
                .build();
    }

    @Test
    void forgotPasswordRetourneLeMemeMessageGeneriquePourUneAdresseInconnue() throws Exception {
        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"inconnu@assrone.ch"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "Si un compte correspond à cette adresse, un email de réinitialisation a été envoyé."));

        verify(passwordResetService).requestReset("inconnu@assrone.ch");
    }

    @Test
    void forgotPasswordRetourneLeMemeMessageGeneriquePourUneAdresseExistante() throws Exception {
        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"membre@assrone.ch"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "Si un compte correspond à cette adresse, un email de réinitialisation a été envoyé."));
    }

    @Test
    void forgotPasswordNormaliseLaCasseDeLEmailAvantDAppelerLeService() throws Exception {
        // Volontairement sans espaces autour : le validateur @Email de la DTO
        // rejette une adresse avec des espaces en tête/fin avant même d'atteindre
        // le contrôleur, donc seule la casse est testée ici — le trim est déjà
        // couvert par UserInfoServiceTest#normaliseEmailAvantLaRechercheEtLaSauvegarde
        // sur le même utilitaire EmailNormalizer.
        mockMvc.perform(post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"Membre@ASSRONE.ch"}
                        """));

        verify(passwordResetService).requestReset("membre@assrone.ch");
    }

    @Test
    void forgotPasswordSansEmailEstRejeteAvec400EtNAppelleJamaisLeService() throws Exception {
        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {}
                                """))
                .andExpect(status().isBadRequest());

        verify(passwordResetService, never()).requestReset(any());
    }

    @Test
    void forgotPasswordAuDelaDeLaLimiteRetourne429SansAppelerLeService() throws Exception {
        ConsumptionProbe refuse = mock(ConsumptionProbe.class);
        when(refuse.isConsumed()).thenReturn(false);
        when(rateLimiterService.tryConsume(anyString(), any())).thenReturn(refuse);

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"membre@assrone.ch"}
                                """))
                .andExpect(status().isTooManyRequests());

        verify(passwordResetService, never()).requestReset(any());
    }

    @Test
    void resetPasswordValideDelegueAuServiceEtRetourneUnMessageDeSucces() throws Exception {
        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"un-token-valide","newPassword":"nouveauMotDePasse123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Votre mot de passe a été réinitialisé. Veuillez vous reconnecter."));

        verify(passwordResetService, times(1)).resetPassword("un-token-valide", "nouveauMotDePasse123");
    }

    @Test
    void resetPasswordAvecUnTokenInvalideRetourne400SansFuiteDeDetailInterne() throws Exception {
        doThrow(new InvalidPasswordResetTokenException("Ce lien de réinitialisation n'est plus valide."))
                .when(passwordResetService).resetPassword(any(), any());

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"token-invalide","newPassword":"nouveauMotDePasse123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"error":"Ce lien de réinitialisation n'est plus valide."}
                        """));
    }

    @Test
    void resetPasswordSansTokenEstRejeteAvec400() throws Exception {
        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"nouveauMotDePasse123"}
                                """))
                .andExpect(status().isBadRequest());

        verify(passwordResetService, never()).resetPassword(any(), any());
    }
}
