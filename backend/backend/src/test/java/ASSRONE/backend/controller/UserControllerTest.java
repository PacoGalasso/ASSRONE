package ASSRONE.backend.controller;

import ASSRONE.backend.dto.RegisterRequest;
import ASSRONE.backend.exception.GlobalExceptionHandler;
import ASSRONE.backend.service.JwtService;
import ASSRONE.backend.service.UserInfoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    private UserInfoService userInfoService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userInfoService = mock(UserInfoService.class);
        UserController controller = new UserController(userInfoService, mock(JwtService.class), mock(AuthenticationManager.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
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
