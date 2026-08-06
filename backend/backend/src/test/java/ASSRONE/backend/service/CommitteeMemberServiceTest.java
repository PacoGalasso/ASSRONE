package ASSRONE.backend.service;

import ASSRONE.backend.audit.AuditLogCapture;
import ASSRONE.backend.audit.SecurityAuditService;
import ASSRONE.backend.dto.CommitteeMemberDto;
import ASSRONE.backend.dto.CreateCommitteeMemberRequest;
import ASSRONE.backend.dto.UpdateCommitteeMemberRequest;
import ASSRONE.backend.exception.InvalidAvatarException;
import ASSRONE.backend.exception.ResourceNotFoundException;
import ASSRONE.backend.mapper.CommitteeMemberMapper;
import ASSRONE.backend.model.CommitteeMember;
import ASSRONE.backend.repository.CommitteeMemberRepository;
import ASSRONE.backend.security.ClientIpResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommitteeMemberServiceTest {

    @Mock
    private CommitteeMemberRepository committeeMemberRepository;

    @Mock
    private CommitteeMemberMapper committeeMemberMapper;

    @TempDir
    private Path uploadDir;

    private CommitteeMemberService service;

    @BeforeEach
    void setUp() {
        service = new CommitteeMemberService(committeeMemberRepository, committeeMemberMapper, new AvatarImageInspector(),
                new SecurityAuditService(new ClientIpResolver("")));
        ReflectionTestUtils.setField(service, "uploadDir", uploadDir.toString());
    }

    private static CommitteeMember existingMember(Long id, String photoFilename, boolean active) {
        return CommitteeMember.builder()
                .id(id)
                .firstName("Isabelle")
                .lastName("Santarelli")
                .role("Membre du comité")
                .displayOrder(6)
                .active(active)
                .photoFilename(photoFilename)
                .build();
    }

    private static byte[] validPngBytes() throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static Authentication authenticationWithRole(String role) {
        return new UsernamePasswordAuthenticationToken(
                "user@assrone.ch", null, List.of(new SimpleGrantedAuthority(role)));
    }

    // ===== Lecture =====

    @Test
    void getPublicMembersInterrogeUniquementLesMembresActifsTriesParOrdreEtNom() {
        service.getPublicMembers();

        verify(committeeMemberRepository).findByActiveTrueOrderByDisplayOrderAscLastNameAsc();
    }

    @Test
    void getAllForAdminInterrogeTousLesMembres() {
        service.getAllForAdmin();

        verify(committeeMemberRepository).findAllByOrderByDisplayOrderAscLastNameAsc();
    }

    // ===== Création =====

    @Test
    void createTrimLesChainesAvantSauvegarde() {
        CreateCommitteeMemberRequest request = CreateCommitteeMemberRequest.builder()
                .firstName(" Jean ").lastName(" Dupont ").role(" Trésorier ")
                .description("  Une description.  ").displayOrder(1).active(true).build();
        CommitteeMember mapped = CommitteeMember.builder()
                .firstName(" Jean ").lastName(" Dupont ").role(" Trésorier ")
                .description("  Une description.  ").displayOrder(1).active(true).build();
        when(committeeMemberMapper.fromCreateRequest(request)).thenReturn(mapped);
        when(committeeMemberRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(committeeMemberMapper.toDto(any())).thenReturn(new CommitteeMemberDto());

        service.create(request);

        ArgumentCaptor<CommitteeMember> captor = ArgumentCaptor.forClass(CommitteeMember.class);
        verify(committeeMemberRepository).save(captor.capture());
        assertThat(captor.getValue().getFirstName()).isEqualTo("Jean");
        assertThat(captor.getValue().getLastName()).isEqualTo("Dupont");
        assertThat(captor.getValue().getRole()).isEqualTo("Trésorier");
        assertThat(captor.getValue().getDescription()).isEqualTo("Une description.");
    }

    @Test
    void createAvecDescriptionUniquementDesEspacesLaConvertitEnNull() {
        CreateCommitteeMemberRequest request = CreateCommitteeMemberRequest.builder()
                .firstName("Jean").lastName("Dupont").role("Trésorier")
                .description("   ").displayOrder(1).active(true).build();
        CommitteeMember mapped = CommitteeMember.builder()
                .firstName("Jean").lastName("Dupont").role("Trésorier")
                .description("   ").displayOrder(1).active(true).build();
        when(committeeMemberMapper.fromCreateRequest(request)).thenReturn(mapped);
        when(committeeMemberRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(committeeMemberMapper.toDto(any())).thenReturn(new CommitteeMemberDto());

        service.create(request);

        ArgumentCaptor<CommitteeMember> captor = ArgumentCaptor.forClass(CommitteeMember.class);
        verify(committeeMemberRepository).save(captor.capture());
        assertThat(captor.getValue().getDescription()).isNull();
    }

    // ===== Modification =====

    @Test
    void updateAvecUnIdInexistantLeveResourceNotFound() {
        when(committeeMemberRepository.findById(42L)).thenReturn(Optional.empty());
        UpdateCommitteeMemberRequest request = UpdateCommitteeMemberRequest.builder()
                .firstName("Jean").lastName("Dupont").role("Trésorier").displayOrder(1).active(true).build();

        assertThatThrownBy(() -> service.update(42L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Membre du comité introuvable : 42");
    }

    @Test
    void updateNeModifieJamaisLePhotoFilenameViaLeMapper() {
        CommitteeMember existing = existingMember(1L, "existing-photo.jpg", true);
        when(committeeMemberRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(committeeMemberRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(committeeMemberMapper.toDto(any())).thenReturn(new CommitteeMemberDto());
        UpdateCommitteeMemberRequest request = UpdateCommitteeMemberRequest.builder()
                .firstName("Jean").lastName("Dupont").role("Trésorier").displayOrder(2).active(false).build();

        service.update(1L, request);

        ArgumentCaptor<CommitteeMember> captor = ArgumentCaptor.forClass(CommitteeMember.class);
        verify(committeeMemberRepository).save(captor.capture());
        assertThat(captor.getValue().getPhotoFilename()).isEqualTo("existing-photo.jpg");
    }

    // ===== Suppression =====

    @Test
    void deleteAvecUnIdInexistantLeveResourceNotFound() {
        when(committeeMemberRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(42L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteSupprimeLaLigneEtLaPhotoAssociee() throws IOException {
        Files.createDirectories(uploadDir.resolve("committee-photos"));
        Files.write(uploadDir.resolve("committee-photos/a-supprimer.jpg"), "contenu".getBytes());
        CommitteeMember existing = existingMember(1L, "a-supprimer.jpg", true);
        when(committeeMemberRepository.findById(1L)).thenReturn(Optional.of(existing));

        service.delete(1L);

        verify(committeeMemberRepository).delete(existing);
        assertThat(uploadDir.resolve("committee-photos/a-supprimer.jpg")).doesNotExist();
    }

    @Test
    void deleteSansPhotoNeSupprimeAucunFichier() {
        CommitteeMember existing = existingMember(1L, null, true);
        when(committeeMemberRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatCode(() -> service.delete(1L)).doesNotThrowAnyException();

        verify(committeeMemberRepository).delete(existing);
    }

    // ===== Journalisation d'audit =====

    @Test
    void creationReussieJournaliseCommitteeMemberCreated() {
        CreateCommitteeMemberRequest request = CreateCommitteeMemberRequest.builder()
                .firstName("Jean").lastName("Dupont").role("Trésorier").displayOrder(1).active(true).build();
        CommitteeMember mapped = CommitteeMember.builder()
                .id(9L).firstName("Jean").lastName("Dupont").role("Trésorier").displayOrder(1).active(true).build();
        when(committeeMemberMapper.fromCreateRequest(request)).thenReturn(mapped);
        when(committeeMemberRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(committeeMemberMapper.toDto(any())).thenReturn(new CommitteeMemberDto());

        try (AuditLogCapture capture = new AuditLogCapture()) {
            service.create(request);

            String line = capture.messages().get(0);
            assertThat(line).contains("eventType=COMMITTEE_MEMBER_CREATED").contains("result=SUCCESS").contains("targetId=9");
        }
    }

    @Test
    void modificationReussieJournaliseCommitteeMemberUpdated() {
        CommitteeMember existing = existingMember(1L, "existing-photo.jpg", true);
        when(committeeMemberRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(committeeMemberRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(committeeMemberMapper.toDto(any())).thenReturn(new CommitteeMemberDto());
        UpdateCommitteeMemberRequest request = UpdateCommitteeMemberRequest.builder()
                .firstName("Jean").lastName("Dupont").role("Trésorier").displayOrder(2).active(false).build();

        try (AuditLogCapture capture = new AuditLogCapture()) {
            service.update(1L, request);

            String line = capture.messages().get(0);
            assertThat(line).contains("eventType=COMMITTEE_MEMBER_UPDATED").contains("result=SUCCESS").contains("targetId=1");
        }
    }

    @Test
    void suppressionReussieJournaliseCommitteeMemberDeleted() {
        CommitteeMember existing = existingMember(1L, null, true);
        when(committeeMemberRepository.findById(1L)).thenReturn(Optional.of(existing));

        try (AuditLogCapture capture = new AuditLogCapture()) {
            service.delete(1L);

            String line = capture.messages().get(0);
            assertThat(line).contains("eventType=COMMITTEE_MEMBER_DELETED").contains("result=SUCCESS").contains("targetId=1");
        }
    }

    // ===== Photo =====

    @Test
    void uploadPhotoAvecUnIdInexistantLeveResourceNotFound() {
        when(committeeMemberRepository.findById(42L)).thenReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1});

        assertThatThrownBy(() -> service.uploadPhoto(42L, file))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void uploadPhotoAvecUnContenuNonImageEstRejete() {
        CommitteeMember existing = existingMember(1L, null, true);
        when(committeeMemberRepository.findById(1L)).thenReturn(Optional.of(existing));
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "pas une image".getBytes());

        assertThatThrownBy(() -> service.uploadPhoto(1L, file))
                .isInstanceOf(InvalidAvatarException.class);

        verify(committeeMemberRepository, Mockito.never()).save(any());
    }

    @Test
    void uploadPhotoValideEcritLeFichierEtMetAJourLaReference() throws IOException {
        CommitteeMember existing = existingMember(1L, null, true);
        when(committeeMemberRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(committeeMemberRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(committeeMemberMapper.toDto(any())).thenReturn(new CommitteeMemberDto());
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", validPngBytes());

        service.uploadPhoto(1L, file);

        ArgumentCaptor<CommitteeMember> captor = ArgumentCaptor.forClass(CommitteeMember.class);
        verify(committeeMemberRepository).save(captor.capture());
        String storedFilename = captor.getValue().getPhotoFilename();
        assertThat(storedFilename).endsWith(".png");
        assertThat(uploadDir.resolve("committee-photos").resolve(storedFilename)).exists();
    }

    @Test
    void uploadPhotoRemplaceSupprimeLAncienneApresSuccesUniquement() throws IOException {
        Files.createDirectories(uploadDir.resolve("committee-photos"));
        Files.write(uploadDir.resolve("committee-photos/ancienne.jpg"), "contenu".getBytes());
        CommitteeMember existing = existingMember(1L, "ancienne.jpg", true);
        when(committeeMemberRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(committeeMemberRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(committeeMemberMapper.toDto(any())).thenReturn(new CommitteeMemberDto());
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", validPngBytes());

        service.uploadPhoto(1L, file);

        assertThat(uploadDir.resolve("committee-photos/ancienne.jpg")).doesNotExist();
    }

    @Test
    void uploadPhotoReussiJournaliseCommitteeMemberPhotoChanged() throws IOException {
        CommitteeMember existing = existingMember(1L, null, true);
        when(committeeMemberRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(committeeMemberRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(committeeMemberMapper.toDto(any())).thenReturn(new CommitteeMemberDto());
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", validPngBytes());

        try (AuditLogCapture capture = new AuditLogCapture()) {
            service.uploadPhoto(1L, file);

            String line = capture.messages().get(0);
            assertThat(line).contains("eventType=COMMITTEE_MEMBER_PHOTO_CHANGED")
                    .contains("result=SUCCESS")
                    .contains("targetId=1");
        }
    }

    @Test
    void uploadPhotoAvecEchecDeSauvegardeConserveLAncienneReference() throws IOException {
        CommitteeMember existing = existingMember(1L, "ancienne.jpg", true);
        when(committeeMemberRepository.findById(1L)).thenReturn(Optional.of(existing));
        RuntimeException dbFailure = new DataAccessResourceFailureException("base indisponible");
        when(committeeMemberRepository.save(any())).thenThrow(dbFailure);
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", validPngBytes());

        assertThatThrownBy(() -> service.uploadPhoto(1L, file)).isSameAs(dbFailure);

        assertThat(existing.getPhotoFilename()).isEqualTo("ancienne.jpg");
        try (var files = Files.list(uploadDir.resolve("committee-photos"))) {
            assertThat(files.anyMatch(p -> !p.getFileName().toString().equals("ancienne.jpg"))).isFalse();
        }
    }

    @Test
    void deletePhotoAvecUnIdInexistantLeveResourceNotFound() {
        when(committeeMemberRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePhoto(42L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deletePhotoSupprimeLeFichierEtLaReference() throws IOException {
        Files.createDirectories(uploadDir.resolve("committee-photos"));
        Files.write(uploadDir.resolve("committee-photos/a-retirer.jpg"), "contenu".getBytes());
        CommitteeMember existing = existingMember(1L, "a-retirer.jpg", true);
        when(committeeMemberRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(committeeMemberRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(committeeMemberMapper.toDto(any())).thenReturn(new CommitteeMemberDto());

        service.deletePhoto(1L);

        ArgumentCaptor<CommitteeMember> captor = ArgumentCaptor.forClass(CommitteeMember.class);
        verify(committeeMemberRepository).save(captor.capture());
        assertThat(captor.getValue().getPhotoFilename()).isNull();
        assertThat(uploadDir.resolve("committee-photos/a-retirer.jpg")).doesNotExist();
    }

    // ===== Visibilité de la photo =====

    @Test
    void loadPhotoInexistantRenvoieNull() throws IOException {
        when(committeeMemberRepository.findById(42L)).thenReturn(Optional.empty());

        Resource resource = service.loadPhoto(42L, null);

        assertThat(resource).isNull();
    }

    @Test
    void loadPhotoSansFichierRenvoieNull() throws IOException {
        CommitteeMember existing = existingMember(1L, null, true);
        when(committeeMemberRepository.findById(1L)).thenReturn(Optional.of(existing));

        Resource resource = service.loadPhoto(1L, null);

        assertThat(resource).isNull();
    }

    @Test
    void loadPhotoDUnMembreInactifPourUnVisiteurAnonymeRenvoieNull() throws IOException {
        CommitteeMember existing = existingMember(1L, "photo.jpg", false);
        when(committeeMemberRepository.findById(1L)).thenReturn(Optional.of(existing));

        Resource resource = service.loadPhoto(1L, null);

        assertThat(resource).isNull();
    }

    @Test
    void loadPhotoDUnMembreInactifPourUnAdminReussit() throws IOException {
        Files.createDirectories(uploadDir.resolve("committee-photos"));
        Files.write(uploadDir.resolve("committee-photos/photo.jpg"), "contenu".getBytes());
        CommitteeMember existing = existingMember(1L, "photo.jpg", false);
        when(committeeMemberRepository.findById(1L)).thenReturn(Optional.of(existing));

        Resource resource = service.loadPhoto(1L, authenticationWithRole("ROLE_ADMIN"));

        assertThat(resource).isNotNull();
        assertThat(resource.exists()).isTrue();
    }

    @Test
    void loadPhotoDUnMembreActifPourNImporteQuiReussit() throws IOException {
        Files.createDirectories(uploadDir.resolve("committee-photos"));
        Files.write(uploadDir.resolve("committee-photos/photo.jpg"), "contenu".getBytes());
        CommitteeMember existing = existingMember(1L, "photo.jpg", true);
        when(committeeMemberRepository.findById(1L)).thenReturn(Optional.of(existing));

        Resource resource = service.loadPhoto(1L, null);

        assertThat(resource).isNotNull();
        assertThat(resource.exists()).isTrue();
    }
}
