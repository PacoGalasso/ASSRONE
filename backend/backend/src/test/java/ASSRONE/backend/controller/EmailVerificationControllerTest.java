package ASSRONE.backend.controller;

import ASSRONE.backend.exception.GlobalExceptionHandler;
import ASSRONE.backend.exception.InvalidEmailVerificationTokenException;
import ASSRONE.backend.ratelimit.RateLimiterService;
import ASSRONE.backend.security.RefreshCookieFactory;
import ASSRONE.backend.service.EmailVerificationService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmailVerificationControllerTest {

    private EmailVerificationService emailVerificationService;
    private RateLimiterService rateLimiterService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        emailVerificationService = mock(EmailVerificationService.class);
        rateLimiterService = mock(RateLimiterService.class);
        ConsumptionProbe consumed = mock(ConsumptionProbe.class);
        when(consumed.isConsumed()).thenReturn(true);
        when(rateLimiterService.tryConsume(anyString(), any())).thenReturn(consumed);

        EmailVerificationController controller = new EmailVerificationController(emailVerificationService, rateLimiterService);
        RefreshCookieFactory refreshCookieFactory = new RefreshCookieFactory("refresh_token", false, "Lax", "/auth");
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(refreshCookieFactory))
                .build();
    }

    @Test
    void verifyEmailValideDelegueAuServiceEtRetourneUnMessageDeSucces() throws Exception {
        mockMvc.perform(post("/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"un-token-valide"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "Votre adresse email a été vérifiée. Vous pouvez maintenant vous connecter."));

        verify(emailVerificationService).verify("un-token-valide");
    }

    @Test
    void verifyEmailAvecUnTokenInvalideRetourne400SansFuiteDeDetailInterne() throws Exception {
        doThrow(new InvalidEmailVerificationTokenException("Ce lien de vérification n'est plus valide."))
                .when(emailVerificationService).verify(any());

        mockMvc.perform(post("/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"token-invalide"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"error":"Ce lien de vérification n'est plus valide."}
                        """));
    }

    @Test
    void verifyEmailSansTokenEstRejeteAvec400() throws Exception {
        mockMvc.perform(post("/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {}
                                """))
                .andExpect(status().isBadRequest());

        verify(emailVerificationService, never()).verify(any());
    }

    @Test
    void resendVerificationRetourneLeMemeMessageGeneriquePourUneAdresseInconnue() throws Exception {
        mockMvc.perform(post("/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"inconnu@assrone.ch"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "Si un compte non vérifié correspond à cette adresse, un email de vérification a été envoyé."));

        verify(emailVerificationService).resend("inconnu@assrone.ch");
    }

    @Test
    void resendVerificationRetourneLeMemeMessageGeneriquePourUnCompteDejaVerifie() throws Exception {
        mockMvc.perform(post("/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"deja-verifie@assrone.ch"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "Si un compte non vérifié correspond à cette adresse, un email de vérification a été envoyé."));
    }

    @Test
    void resendVerificationNormaliseLaCasseDeLEmailAvantDAppelerLeService() throws Exception {
        mockMvc.perform(post("/auth/resend-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"Membre@ASSRONE.ch"}
                        """));

        verify(emailVerificationService).resend("membre@assrone.ch");
    }

    @Test
    void resendVerificationAuDelaDeLaLimiteRetourne429SansAppelerLeService() throws Exception {
        ConsumptionProbe refuse = mock(ConsumptionProbe.class);
        when(refuse.isConsumed()).thenReturn(false);
        when(rateLimiterService.tryConsume(anyString(), any())).thenReturn(refuse);

        mockMvc.perform(post("/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"membre@assrone.ch"}
                                """))
                .andExpect(status().isTooManyRequests());

        verify(emailVerificationService, never()).resend(any());
    }
}
