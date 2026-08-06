package ASSRONE.backend.controller;

import ASSRONE.backend.audit.SecurityAuditService;
import ASSRONE.backend.audit.SecurityEventResult;
import ASSRONE.backend.audit.SecurityEventType;
import ASSRONE.backend.dto.RegisterRequest;
import ASSRONE.backend.dto.RegisterResponse;
import ASSRONE.backend.exception.GlobalExceptionHandler;
import ASSRONE.backend.exception.InvalidRefreshTokenException;
import ASSRONE.backend.security.ClientIpResolver;
import ASSRONE.backend.security.RefreshCookieFactory;
import ASSRONE.backend.service.LoginAttemptService;
import ASSRONE.backend.service.RefreshTokenService;
import ASSRONE.backend.service.UserInfoService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    private static final String LOGIN_INVALIDE = """
            {"email":"membre@assrone.ch","password":"mauvais-mot-de-passe"}
            """;
    private static final String CORPS_ERREUR_GENERIQUE = "{\"error\":\"Email ou mot de passe incorrect.\"}";
    private static final String REFRESH_COOKIE_NAME = "refresh_token";

    private UserInfoService userInfoService;
    private AuthenticationManager authenticationManager;
    private RefreshTokenService refreshTokenService;
    private LoginAttemptService loginAttemptService;
    private SecurityAuditService securityAuditService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userInfoService = mock(UserInfoService.class);
        authenticationManager = mock(AuthenticationManager.class);
        refreshTokenService = mock(RefreshTokenService.class);
        loginAttemptService = mock(LoginAttemptService.class);
        securityAuditService = mock(SecurityAuditService.class);
        RefreshCookieFactory refreshCookieFactory = new RefreshCookieFactory(REFRESH_COOKIE_NAME, false, "Lax", "/auth");
        UserController controller = new UserController(userInfoService, refreshTokenService, authenticationManager,
                loginAttemptService, refreshCookieFactory, securityAuditService, new ClientIpResolver(""));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(refreshCookieFactory))
                .build();
    }

    @Test
    void motDePasseIncorrectRetourne401AvecMessageGenerique() throws Exception {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/auth/generateToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_INVALIDE))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(CORPS_ERREUR_GENERIQUE));
    }

    @Test
    void motDePasseAbsentRejeteAvec400EtNAtteintJamaisLAuthenticationManager() throws Exception {
        mockMvc.perform(post("/auth/generateToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"membre@assrone.ch"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authenticationManager);
    }

    @Test
    void emailAbsentRejeteAvec400EtNAtteintJamaisLAuthenticationManager() throws Exception {
        mockMvc.perform(post("/auth/generateToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"bon-mot-de-passe"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authenticationManager);
    }

    @Test
    void compteDesactiveRetourneExactementLeMemeStatutEtLeMemeMessage() throws Exception {
        when(authenticationManager.authenticate(any())).thenThrow(new DisabledException("Disabled"));

        mockMvc.perform(post("/auth/generateToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_INVALIDE))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(CORPS_ERREUR_GENERIQUE));
    }

    @Test
    void compteVerrouilleRetourneExactementLeMemeStatutEtLeMemeMessage() throws Exception {
        when(authenticationManager.authenticate(any())).thenThrow(new LockedException("Locked"));

        mockMvc.perform(post("/auth/generateToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_INVALIDE))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(CORPS_ERREUR_GENERIQUE));
    }

    @Test
    void lesTroisCausesDEchecProduisentUneReponseStrictementIdentique() throws Exception {
        Mockito.doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager).authenticate(any());
        MvcResult mauvaisMotDePasse = mockMvc.perform(post("/auth/generateToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_INVALIDE))
                .andReturn();

        Mockito.doThrow(new DisabledException("Disabled"))
                .when(authenticationManager).authenticate(any());
        MvcResult compteDesactive = mockMvc.perform(post("/auth/generateToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_INVALIDE))
                .andReturn();

        assertThat(mauvaisMotDePasse.getResponse().getStatus()).isEqualTo(compteDesactive.getResponse().getStatus());
        assertThat(mauvaisMotDePasse.getResponse().getContentAsString())
                .isEqualTo(compteDesactive.getResponse().getContentAsString());
    }

    @Test
    void connexionValideRetourneLesTokens() throws Exception {
        UsernamePasswordAuthenticationToken authentifie = new UsernamePasswordAuthenticationToken(
                "membre@assrone.ch", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(authenticationManager.authenticate(any())).thenReturn(authentifie);
        when(refreshTokenService.issueTokens(eq("membre@assrone.ch"), any(), any())).thenReturn(
                new RefreshTokenService.IssuedTokens("access-token", "refresh-token", "ROLE_USER", "membre@assrone.ch",
                        Duration.ofDays(7), 1L));

        mockMvc.perform(post("/auth/generateToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"membre@assrone.ch","password":"bon-mot-de-passe"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"token":"access-token","username":"membre@assrone.ch","role":"ROLE_USER"}
                        """))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().exists(REFRESH_COOKIE_NAME))
                .andExpect(cookie().value(REFRESH_COOKIE_NAME, "refresh-token"))
                .andExpect(cookie().httpOnly(REFRESH_COOKIE_NAME, true))
                .andExpect(cookie().secure(REFRESH_COOKIE_NAME, false))
                .andExpect(cookie().path(REFRESH_COOKIE_NAME, "/auth"));
    }

    @Test
    void connexionValideJournaliseLoginSuccessAvecIdInterne() throws Exception {
        UsernamePasswordAuthenticationToken authentifie = new UsernamePasswordAuthenticationToken(
                "membre@assrone.ch", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(authenticationManager.authenticate(any())).thenReturn(authentifie);
        when(refreshTokenService.issueTokens(eq("membre@assrone.ch"), any(), any())).thenReturn(
                new RefreshTokenService.IssuedTokens("access-token", "refresh-token", "ROLE_USER", "membre@assrone.ch",
                        Duration.ofDays(7), 1L));

        mockMvc.perform(post("/auth/generateToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"membre@assrone.ch","password":"bon-mot-de-passe"}
                        """));

        verify(securityAuditService).record(SecurityEventType.LOGIN_SUCCESS, SecurityEventResult.SUCCESS,
                "1", "ROLE_USER", "user", "1", null);
    }

    @Test
    void echecAvecMauvaisMotDePasseJournaliseLoginFailureSansMotDePasseNiToken() throws Exception {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));
        when(loginAttemptService.isCurrentlyLocked("membre@assrone.ch")).thenReturn(false);

        mockMvc.perform(post("/auth/generateToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(LOGIN_INVALIDE));

        ArgumentCaptor<String> actorIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(securityAuditService).record(eq(SecurityEventType.LOGIN_FAILURE), eq(SecurityEventResult.DENIED),
                actorIdCaptor.capture(), eq(null), eq("user"), eq(null), eq("BAD_CREDENTIALS"));
        assertThat(actorIdCaptor.getValue()).doesNotContain("mauvais-mot-de-passe");
    }

    @Test
    void compteVerrouilleJournaliseLoginFailureAvecReasonCodeAccountLocked() throws Exception {
        when(authenticationManager.authenticate(any())).thenThrow(new LockedException("Locked"));

        mockMvc.perform(post("/auth/generateToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(LOGIN_INVALIDE));

        verify(securityAuditService).record(eq(SecurityEventType.LOGIN_FAILURE), eq(SecurityEventResult.DENIED),
                any(), eq(null), eq("user"), eq(null), eq("ACCOUNT_LOCKED"));
        verifyNoInteractions(loginAttemptService);
    }

    @Test
    void compteDesactiveJournaliseLoginFailureAvecReasonCodeAccountDisabled() throws Exception {
        when(authenticationManager.authenticate(any())).thenThrow(new DisabledException("Disabled"));

        mockMvc.perform(post("/auth/generateToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(LOGIN_INVALIDE));

        verify(securityAuditService).record(eq(SecurityEventType.LOGIN_FAILURE), eq(SecurityEventResult.DENIED),
                any(), eq(null), eq("user"), eq(null), eq("ACCOUNT_DISABLED"));
    }

    @Test
    void echecQuiFranchitLeSeuilJournaliseAccountLockedPasLoginFailure() throws Exception {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));
        when(loginAttemptService.isCurrentlyLocked("membre@assrone.ch")).thenReturn(true);

        mockMvc.perform(post("/auth/generateToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(LOGIN_INVALIDE));

        verify(securityAuditService).record(eq(SecurityEventType.ACCOUNT_LOCKED), eq(SecurityEventResult.DENIED),
                any(), eq(null), eq("user"), eq(null), eq("THRESHOLD_REACHED"));
        verify(securityAuditService, Mockito.never()).record(eq(SecurityEventType.LOGIN_FAILURE), any(), any(), any(), any(), any(), any());
    }

    @Test
    void echecEnregistreUneTentativeAvecLEmailNormalise() throws Exception {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/auth/generateToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"  Membre@ASSRONE.ch  ","password":"mauvais-mot-de-passe"}
                        """));

        verify(loginAttemptService).registerFailedAttempt("membre@assrone.ch");
    }

    @Test
    void compteVerrouilleNemetAucunJwt() throws Exception {
        when(authenticationManager.authenticate(any())).thenThrow(new LockedException("Locked"));

        mockMvc.perform(post("/auth/generateToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_INVALIDE))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void succesReinitialiseLeCompteurAvantEmissionDesTokens() throws Exception {
        UsernamePasswordAuthenticationToken authentifie = new UsernamePasswordAuthenticationToken(
                "membre@assrone.ch", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(authenticationManager.authenticate(any())).thenReturn(authentifie);
        when(refreshTokenService.issueTokens(any(), any(), any())).thenReturn(
                new RefreshTokenService.IssuedTokens("access-token", "refresh-token", "ROLE_USER", "membre@assrone.ch",
                        Duration.ofDays(7), 1L));

        mockMvc.perform(post("/auth/generateToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"membre@assrone.ch","password":"bon-mot-de-passe"}
                                """))
                .andExpect(status().isOk());

        InOrder ordre = inOrder(loginAttemptService, refreshTokenService);
        ordre.verify(loginAttemptService).resetFailedAttempts("membre@assrone.ch");
        ordre.verify(refreshTokenService).issueTokens(eq("membre@assrone.ch"), any(), any());
    }

    @Test
    void panneDuServiceDeCompteurNestJamaisMasqueeEnFaux401() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));
        Mockito.doThrow(new DataAccessResourceFailureException("Postgres indisponible"))
                .when(loginAttemptService).registerFailedAttempt(any());

        assertThatThrownBy(() -> mockMvc.perform(post("/auth/generateToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_INVALIDE)))
                .hasRootCauseInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void inscriptionValideRetourneLeContratTypeAvecLesChampsPublics() throws Exception {
        when(userInfoService.addUser(any(RegisterRequest.class))).thenReturn(
                RegisterResponse.builder()
                        .id(1L)
                        .username("jdupont")
                        .email("jean.dupont@assrone.ch")
                        .firstName("Jean")
                        .lastName("Dupont")
                        .build());

        mockMvc.perform(post("/auth/addNewUser")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"jdupont","email":"jean.dupont@assrone.ch",
                                 "firstName":"Jean","lastName":"Dupont","password":"motdepasse123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("jdupont"))
                .andExpect(jsonPath("$.email").value("jean.dupont@assrone.ch"))
                .andExpect(jsonPath("$.firstName").value("Jean"))
                .andExpect(jsonPath("$.lastName").value("Dupont"));
    }

    @Test
    void champsSensiblesDuJsonNAtteignentJamaisLeDtoTransmisAuService() throws Exception {
        when(userInfoService.addUser(any(RegisterRequest.class))).thenReturn(
                RegisterResponse.builder()
                        .id(1L)
                        .username("attaquant")
                        .email("attaquant@x.ch")
                        .firstName("A")
                        .lastName("B")
                        .build());
        ArgumentCaptor<RegisterRequest> captor = ArgumentCaptor.forClass(RegisterRequest.class);

        mockMvc.perform(post("/auth/addNewUser")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"attaquant","email":"attaquant@x.ch",
                                 "firstName":"A","lastName":"B","password":"motdepasse123",
                                 "role":"ADMIN","isActive":false,"id":999}
                                """))
                .andExpect(status().isOk());

        verify(userInfoService).addUser(captor.capture());
        RegisterRequest captured = captor.getValue();
        assertThat(captured.getUsername()).isEqualTo("attaquant");
        assertThat(captured.getEmail()).isEqualTo("attaquant@x.ch");
        // RegisterRequest ne déclare ni role, ni isActive, ni id : ces champs du JSON
        // ne peuvent structurellement pas atteindre l'objet transmis au service.
    }

    @Test
    void emailInvalideRejeteAvec400() throws Exception {
        mockMvc.perform(post("/auth/addNewUser")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"jdupont","email":"pas-un-email",
                                 "firstName":"Jean","lastName":"Dupont","password":"motdepasse123"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void motDePasseTropCourtRejeteAvec400() throws Exception {
        mockMvc.perform(post("/auth/addNewUser")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"jdupont","email":"jean.dupont@assrone.ch",
                                 "firstName":"Jean","lastName":"Dupont","password":"court"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reponseNeContientAucunMotDePasseNiChampSensible() throws Exception {
        when(userInfoService.addUser(any(RegisterRequest.class))).thenReturn(
                RegisterResponse.builder()
                        .id(1L)
                        .username("jdupont")
                        .email("jean.dupont@assrone.ch")
                        .firstName("Jean")
                        .lastName("Dupont")
                        .build());

        mockMvc.perform(post("/auth/addNewUser")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"jdupont","email":"jean.dupont@assrone.ch",
                                 "firstName":"Jean","lastName":"Dupont","password":"motdepasse123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.isActive").doesNotExist());
    }

    @Test
    void refreshValideRetourneUnNouveauCoupleDeTokens() throws Exception {
        when(refreshTokenService.rotate(eq("ancien-refresh-token"), any())).thenReturn(
                new RefreshTokenService.IssuedTokens("nouveau-access-token", "nouveau-refresh-token", "ROLE_USER", "membre@assrone.ch",
                        Duration.ofDays(7), 1L));

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, "ancien-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"token":"nouveau-access-token","username":"membre@assrone.ch","role":"ROLE_USER"}
                        """))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().value(REFRESH_COOKIE_NAME, "nouveau-refresh-token"))
                .andExpect(cookie().httpOnly(REFRESH_COOKIE_NAME, true));
    }

    @Test
    void refreshInvalideRetourne401SansDetailInterne() throws Exception {
        when(refreshTokenService.rotate(eq("token-invalide"), any()))
                .thenThrow(new InvalidRefreshTokenException("Refresh token invalide ou expiré."));

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, "token-invalide")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("""
                        {"error":"Refresh token invalide ou expiré."}
                        """))
                .andExpect(cookie().maxAge(REFRESH_COOKIE_NAME, 0));
    }

    @Test
    void refreshSansCookieRejeteAvec401SansAppelerLeService() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("""
                        {"error":"Refresh token invalide ou expiré."}
                        """));

        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void refreshAvecCookieVideRejeteAvec401SansAppelerLeService() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, "")))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void logoutAvecUnRefreshTokenLeRevoqueEtRetourne204() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, "un-refresh-token")))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(REFRESH_COOKIE_NAME, 0));

        verify(refreshTokenService, times(1)).revoke(eq("un-refresh-token"));
    }

    @Test
    void logoutSansCookieRetourne204EtRevoqueQuandMemeAvecUneValeurNulle() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(REFRESH_COOKIE_NAME, 0));

        verify(refreshTokenService, times(1)).revoke(null);
    }

    @Test
    void doubleLogoutResteIdempotent() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, "un-refresh-token")))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/logout")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, "un-refresh-token")))
                .andExpect(status().isNoContent());

        verify(refreshTokenService, times(2)).revoke(eq("un-refresh-token"));
    }
}
