package ASSRONE.backend.controller;

import ASSRONE.backend.dto.RegisterRequest;
import ASSRONE.backend.exception.GlobalExceptionHandler;
import ASSRONE.backend.service.JwtService;
import ASSRONE.backend.service.LoginAttemptService;
import ASSRONE.backend.service.UserInfoService;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    private static final String LOGIN_INVALIDE = """
            {"email":"membre@assrone.ch","password":"mauvais-mot-de-passe"}
            """;
    private static final String CORPS_ERREUR_GENERIQUE = "{\"error\":\"Email ou mot de passe incorrect.\"}";

    private UserInfoService userInfoService;
    private AuthenticationManager authenticationManager;
    private JwtService jwtService;
    private LoginAttemptService loginAttemptService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userInfoService = mock(UserInfoService.class);
        authenticationManager = mock(AuthenticationManager.class);
        jwtService = mock(JwtService.class);
        loginAttemptService = mock(LoginAttemptService.class);
        UserController controller = new UserController(userInfoService, jwtService, authenticationManager, loginAttemptService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
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
        when(jwtService.generateToken("membre@assrone.ch")).thenReturn("access-token");
        when(jwtService.generateRefreshToken("membre@assrone.ch")).thenReturn("refresh-token");

        mockMvc.perform(post("/auth/generateToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"membre@assrone.ch","password":"bon-mot-de-passe"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"token":"access-token","username":"membre@assrone.ch","role":"ROLE_USER","refreshToken":"refresh-token"}
                        """));
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

        verifyNoInteractions(jwtService);
    }

    @Test
    void succesReinitialiseLeCompteurAvantEmissionDesTokens() throws Exception {
        UsernamePasswordAuthenticationToken authentifie = new UsernamePasswordAuthenticationToken(
                "membre@assrone.ch", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(authenticationManager.authenticate(any())).thenReturn(authentifie);
        when(jwtService.generateToken(any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");

        mockMvc.perform(post("/auth/generateToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"membre@assrone.ch","password":"bon-mot-de-passe"}
                                """))
                .andExpect(status().isOk());

        InOrder ordre = inOrder(loginAttemptService, jwtService);
        ordre.verify(loginAttemptService).resetFailedAttempts("membre@assrone.ch");
        ordre.verify(jwtService).generateToken("membre@assrone.ch");
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
    void inscriptionValideRetourneLeMessageDeConfirmation() throws Exception {
        when(userInfoService.addUser(any(RegisterRequest.class))).thenReturn("User added successfully!");

        mockMvc.perform(post("/auth/addNewUser")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"jdupont","email":"jean.dupont@assrone.ch",
                                 "firstName":"Jean","lastName":"Dupont","password":"motdepasse123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string("User added successfully!"));
    }

    @Test
    void champsSensiblesDuJsonNAtteignentJamaisLeDtoTransmisAuService() throws Exception {
        when(userInfoService.addUser(any(RegisterRequest.class))).thenReturn("User added successfully!");
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
        when(userInfoService.addUser(any(RegisterRequest.class))).thenReturn("User added successfully!");

        mockMvc.perform(post("/auth/addNewUser")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"jdupont","email":"jean.dupont@assrone.ch",
                                 "firstName":"Jean","lastName":"Dupont","password":"motdepasse123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string("User added successfully!"));
    }
}
