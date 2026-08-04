package ASSRONE.backend.controller;

import ASSRONE.backend.dto.CommitteeMemberDto;
import ASSRONE.backend.exception.GlobalExceptionHandler;
import ASSRONE.backend.exception.ResourceNotFoundException;
import ASSRONE.backend.service.CommitteeMemberService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommitteeMemberControllerTest {

    private CommitteeMemberService committeeMemberService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        committeeMemberService = mock(CommitteeMemberService.class);
        CommitteeMemberController controller = new CommitteeMemberController(committeeMemberService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String validCreatePayload() throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "firstName", "Jean",
                "lastName", "Dupont",
                "role", "Trésorier",
                "displayOrder", 1,
                "active", true
        ));
    }

    // ===== Lecture =====

    @Test
    void getPublicMembersDelegueAuServiceEtRenvoie200() throws Exception {
        when(committeeMemberService.getPublicMembers()).thenReturn(List.of(new CommitteeMemberDto()));

        mockMvc.perform(get("/api/committee-members"))
                .andExpect(status().isOk());

        verify(committeeMemberService).getPublicMembers();
    }

    @Test
    void getAllForAdminDelegueAuService() throws Exception {
        when(committeeMemberService.getAllForAdmin()).thenReturn(List.of());

        mockMvc.perform(get("/api/committee-members/admin"))
                .andExpect(status().isOk());

        verify(committeeMemberService).getAllForAdmin();
    }

    // ===== Création =====

    @Test
    void createAvecPayloadValideRenvoie201() throws Exception {
        when(committeeMemberService.create(any())).thenReturn(new CommitteeMemberDto());

        mockMvc.perform(post("/api/committee-members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreatePayload()))
                .andExpect(status().isCreated());
    }

    @Test
    void createSansPrenomRenvoie400() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "lastName", "Dupont", "role", "Trésorier", "displayOrder", 1, "active", true));

        mockMvc.perform(post("/api/committee-members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSansOrdreDAffichageRenvoie400() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "firstName", "Jean", "lastName", "Dupont", "role", "Trésorier", "active", true));

        mockMvc.perform(post("/api/committee-members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSansVisibiliteRenvoie400() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "firstName", "Jean", "lastName", "Dupont", "role", "Trésorier", "displayOrder", 1));

        mockMvc.perform(post("/api/committee-members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAvecDescriptionTropLongueRenvoie400() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "firstName", "Jean", "lastName", "Dupont", "role", "Trésorier",
                "description", "A".repeat(1201), "displayOrder", 1, "active", true));

        mockMvc.perform(post("/api/committee-members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAvecDescriptionDeExactement1200CaracteresRenvoie201() throws Exception {
        when(committeeMemberService.create(any())).thenReturn(new CommitteeMemberDto());
        String payload = objectMapper.writeValueAsString(Map.of(
                "firstName", "Jean", "lastName", "Dupont", "role", "Trésorier",
                "description", "A".repeat(1200), "displayOrder", 1, "active", true));

        mockMvc.perform(post("/api/committee-members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());
    }

    // ===== Modification / suppression =====

    @Test
    void updateAvecFicheInexistanteRenvoie404() throws Exception {
        when(committeeMemberService.update(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Membre du comité introuvable : 99"));

        mockMvc.perform(put("/api/committee-members/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreatePayload()))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateAvecDescriptionTropLongueRenvoie400() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "firstName", "Jean", "lastName", "Dupont", "role", "Trésorier",
                "description", "A".repeat(1201), "displayOrder", 1, "active", true));

        mockMvc.perform(put("/api/committee-members/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateAvecDescriptionDeExactement1200CaracteresReussit() throws Exception {
        when(committeeMemberService.update(eq(1L), any())).thenReturn(new CommitteeMemberDto());
        String payload = objectMapper.writeValueAsString(Map.of(
                "firstName", "Jean", "lastName", "Dupont", "role", "Trésorier",
                "description", "A".repeat(1200), "displayOrder", 1, "active", true));

        mockMvc.perform(put("/api/committee-members/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }

    @Test
    void deleteRenvoie204() throws Exception {
        mockMvc.perform(delete("/api/committee-members/1"))
                .andExpect(status().isNoContent());

        verify(committeeMemberService).delete(1L);
    }

    // ===== Photo =====

    @Test
    void uploadPhotoDelegueAuService() throws Exception {
        when(committeeMemberService.uploadPhoto(eq(1L), any())).thenReturn(new CommitteeMemberDto());
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/committee-members/1/photo").file(file))
                .andExpect(status().isOk());
    }

    @Test
    void deletePhotoDelegueAuService() throws Exception {
        when(committeeMemberService.deletePhoto(1L)).thenReturn(new CommitteeMemberDto());

        mockMvc.perform(delete("/api/committee-members/1/photo"))
                .andExpect(status().isOk());
    }

    @Test
    void getPhotoInexistantRenvoie404() throws Exception {
        when(committeeMemberService.loadPhoto(eq(99L), any())).thenReturn(null);

        mockMvc.perform(get("/api/committee-members/99/photo"))
                .andExpect(status().isNotFound());
    }
}
