package ASSRONE.backend.controller;

import ASSRONE.backend.exception.GlobalExceptionHandler;
import ASSRONE.backend.security.RefreshCookieFactory;
import ASSRONE.backend.exception.InvalidAvatarException;
import ASSRONE.backend.service.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileControllerTest {

    private UserProfileService userProfileService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userProfileService = mock(UserProfileService.class);
        ProfileController controller = new ProfileController(userProfileService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(new RefreshCookieFactory("refresh_token", false, "Lax", "/auth")))
                .build();
    }

    @Test
    void unAvatarInvalideRetourne400AvecLeContratJsonExactSansDetailTechnique() throws Exception {
        doThrow(new InvalidAvatarException("Seuls les formats JPEG et PNG sont acceptés."))
                .when(userProfileService).uploadAvatar(eq("membre@assrone.ch"), any());

        MockMultipartFile file = new MockMultipartFile("file", "avatar.txt", "text/plain", "pas une image".getBytes());
        var principal = new UsernamePasswordAuthenticationToken("membre@assrone.ch", null);

        mockMvc.perform(multipart("/api/profile/avatar").file(file).principal(principal))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Seuls les formats JPEG et PNG sont acceptés."))
                .andExpect(jsonPath("$.length()").value(1));
    }
}
