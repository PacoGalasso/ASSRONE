package ASSRONE.backend.service;

import ASSRONE.backend.dto.DocumentDto;
import ASSRONE.backend.exception.InvalidDocumentException;
import ASSRONE.backend.exception.ResourceNotFoundException;
import ASSRONE.backend.mapper.DocumentMapper;
import ASSRONE.backend.model.Document;
import ASSRONE.backend.model.DocumentVisibility;
import ASSRONE.backend.repository.DocumentRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentMapper documentMapper;

    @TempDir
    private Path uploadDir;

    private DocumentService service;

    @BeforeEach
    void setUp() {
        service = new DocumentService(documentRepository, documentMapper, new PdfDocumentInspector());
        ReflectionTestUtils.setField(service, "uploadDir", uploadDir.toString());
    }

    private static Document existingDocument(String storedFilename) {
        return Document.builder()
                .id(1L)
                .title("Statuts")
                .description("Statuts de l'association")
                .originalFilename("statuts.pdf")
                .storedFilename(storedFilename)
                .contentType("application/pdf")
                .fileSize(4L)
                .uploadedBy("admin@assrone.ch")
                .build();
    }

    private void stubSuccessfulSave() {
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(documentMapper.toDto(any())).thenReturn(new DocumentDto());
    }

    private static byte[] validPdfBytes() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] encryptedPdfBytes() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            StandardProtectionPolicy policy =
                    new StandardProtectionPolicy("proprietaire", "utilisateur", new AccessPermission());
            document.protect(policy);
            document.save(output);
            return output.toByteArray();
        }
    }

    private DocumentService serviceWithInspector(PdfDocumentInspector inspector) {
        DocumentService instance = new DocumentService(documentRepository, documentMapper, inspector);
        ReflectionTestUtils.setField(instance, "uploadDir", uploadDir.toString());
        return instance;
    }

    // ===== Rejets =====

    @Test
    void uploadAvecUnMultipartNulEstRejete() {
        assertThatThrownBy(() -> service.upload(null, "Titre", "Description", "admin@assrone.ch", DocumentVisibility.MEMBERS))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessage("Le document est vide.");
    }

    @Test
    void uploadAvecUnMultipartVideEstRejete() {
        MockMultipartFile file = new MockMultipartFile("file", "vide.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service.upload(file, "Titre", "Description", "admin@assrone.ch", DocumentVisibility.MEMBERS))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessage("Le document est vide.");
    }

    @Test
    void uploadAvecUnContenuNonPdfEstRejete() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "ceci n'est pas un PDF".getBytes());

        assertThatThrownBy(() -> service.upload(file, "Titre", "Description", "admin@assrone.ch", DocumentVisibility.MEMBERS))
                .isInstanceOf(InvalidDocumentException.class);
    }

    @Test
    void uploadAvecUnPdfCorrompuEstRejete() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "corrompu.pdf", "application/pdf", "%PDF-1.7\nstructure invalide".getBytes());

        assertThatThrownBy(() -> service.upload(file, "Titre", "Description", "admin@assrone.ch", DocumentVisibility.MEMBERS))
                .isInstanceOf(InvalidDocumentException.class);
    }

    @Test
    void uploadAvecUnPdfChiffreEstRejete() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "chiffre.pdf", "application/pdf", encryptedPdfBytes());

        assertThatThrownBy(() -> service.upload(file, "Titre", "Description", "admin@assrone.ch", DocumentVisibility.MEMBERS))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessage("Les documents PDF protégés par mot de passe ne sont pas acceptés.");
    }

    @Test
    void uploadAvecValidationEnEchecNAppelleJamaisLaSauvegardeDb() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "pas un pdf".getBytes());

        assertThatThrownBy(() -> service.upload(file, "Titre", "Description", "admin@assrone.ch", DocumentVisibility.MEMBERS))
                .isInstanceOf(InvalidDocumentException.class);

        verify(documentRepository, Mockito.never()).save(any());
    }

    @Test
    void uploadAvecValidationEnEchecNeLaisseAucunFichierFinal() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "pas un pdf".getBytes());

        assertThatThrownBy(() -> service.upload(file, "Titre", "Description", "admin@assrone.ch", DocumentVisibility.MEMBERS))
                .isInstanceOf(InvalidDocumentException.class);

        try (var files = Files.list(uploadDir)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void uploadAvecValidationEnEchecNeLaisseAucunFichierTemporaireResiduel() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "pas un pdf".getBytes());

        assertThatThrownBy(() -> service.upload(file, "Titre", "Description", "admin@assrone.ch", DocumentVisibility.MEMBERS))
                .isInstanceOf(InvalidDocumentException.class);

        try (var files = Files.list(uploadDir)) {
            assertThat(files.anyMatch(p -> p.getFileName().toString().startsWith("upload-"))).isFalse();
        }
    }

    // ===== Succès =====

    @Test
    void uploadAppelleLInspecteurAvantLaSauvegardeDb() throws IOException {
        PdfDocumentInspector mockInspector = mock(PdfDocumentInspector.class);
        DocumentService instance = serviceWithInspector(mockInspector);
        stubSuccessfulSave();
        MockMultipartFile file = new MockMultipartFile("file", "rapport.pdf", "application/pdf", validPdfBytes());

        instance.upload(file, "Titre", "Description", "admin@assrone.ch", DocumentVisibility.MEMBERS);

        InOrder inOrder = Mockito.inOrder(mockInspector, documentRepository);
        inOrder.verify(mockInspector).inspect(any());
        inOrder.verify(documentRepository).save(any());
    }

    @Test
    void uploadAppelleLInspecteurAvecLeFichierTemporaire() throws IOException {
        PdfDocumentInspector mockInspector = mock(PdfDocumentInspector.class);
        DocumentService instance = serviceWithInspector(mockInspector);
        stubSuccessfulSave();
        MockMultipartFile file = new MockMultipartFile("file", "rapport.pdf", "application/pdf", validPdfBytes());

        instance.upload(file, "Titre", "Description", "admin@assrone.ch", DocumentVisibility.MEMBERS);

        ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
        verify(mockInspector).inspect(pathCaptor.capture());
        assertThat(pathCaptor.getValue().getFileName().toString()).startsWith("upload-").endsWith(".tmp");
    }

    @Test
    void leMultipartNEstLuQuUneSeuleFois() throws IOException {
        stubSuccessfulSave();
        byte[] contenu = validPdfBytes();
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn((long) contenu.length);
        when(mockFile.getOriginalFilename()).thenReturn("rapport.pdf");
        when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream(contenu));

        service.upload(mockFile, "Titre", "Description", "admin@assrone.ch", DocumentVisibility.MEMBERS);

        verify(mockFile, Mockito.times(1)).getInputStream();
    }

    @Test
    void uploadGenereUnNomPhysiqueUuidSansExtension() throws IOException {
        stubSuccessfulSave();
        MockMultipartFile file = new MockMultipartFile("file", "rapport.pdf", "application/pdf", validPdfBytes());

        service.upload(file, "Titre", "Description", "admin@assrone.ch", DocumentVisibility.MEMBERS);

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getStoredFilename())
                .matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    }

    @Test
    void uploadIgnoreLExtensionClientPourLeNomPhysique() throws IOException {
        stubSuccessfulSave();
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../../etc/passwd.pdf", "application/pdf", validPdfBytes());

        service.upload(file, "Titre", "Description", "admin@assrone.ch", DocumentVisibility.MEMBERS);

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getStoredFilename())
                .doesNotContain("etc")
                .doesNotContain("passwd")
                .doesNotContain("..")
                .doesNotContain(".pdf");
    }

    @Test
    void uploadIgnoreLeMimeClientFalsifiePourLaPersistance() throws IOException {
        stubSuccessfulSave();
        MockMultipartFile file = new MockMultipartFile("file", "rapport.pdf", "text/html", validPdfBytes());

        service.upload(file, "Titre", "Description", "admin@assrone.ch", DocumentVisibility.MEMBERS);

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getContentType()).isEqualTo(MediaType.APPLICATION_PDF_VALUE);
    }

    @Test
    void uploadPersisteExactementApplicationPdf() throws IOException {
        stubSuccessfulSave();
        MockMultipartFile file = new MockMultipartFile("file", "rapport.pdf", "application/pdf", validPdfBytes());

        service.upload(file, "Titre", "Description", "admin@assrone.ch", DocumentVisibility.MEMBERS);

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getContentType()).isEqualTo("application/pdf");
    }

    @Test
    void uploadConserveLeNomOriginalUniquementCommeMetadonnee() throws IOException {
        stubSuccessfulSave();
        MockMultipartFile file = new MockMultipartFile("file", "rapport-annuel.pdf", "application/pdf", validPdfBytes());

        service.upload(file, "Titre", "Description", "admin@assrone.ch", DocumentVisibility.MEMBERS);

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getOriginalFilename()).isEqualTo("rapport-annuel.pdf");
        assertThat(captor.getValue().getStoredFilename()).isNotEqualTo("rapport-annuel.pdf");
    }

    @Test
    void uploadEcritLeFichierFinalSousLaRacineAutorisee() throws IOException {
        stubSuccessfulSave();
        MockMultipartFile file = new MockMultipartFile("file", "rapport.pdf", "application/pdf", validPdfBytes());

        service.upload(file, "Titre", "Description", "admin@assrone.ch", DocumentVisibility.MEMBERS);

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        Path stored = uploadDir.resolve(captor.getValue().getStoredFilename());
        assertThat(stored).exists();
        assertThat(stored.normalize()).startsWith(uploadDir.normalize());
    }

    @Test
    void uploadFichierFinalIdentiqueAuxOctetsPdfRecus() throws IOException {
        stubSuccessfulSave();
        byte[] contenu = validPdfBytes();
        MockMultipartFile file = new MockMultipartFile("file", "rapport.pdf", "application/pdf", contenu);

        service.upload(file, "Titre", "Description", "admin@assrone.ch", DocumentVisibility.MEMBERS);

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        Path stored = uploadDir.resolve(captor.getValue().getStoredFilename());
        assertThat(Files.readAllBytes(stored)).isEqualTo(contenu);
    }

    @Test
    void uploadReussiNeLaisseAucunFichierTemporaireResiduel() throws IOException {
        stubSuccessfulSave();
        MockMultipartFile file = new MockMultipartFile("file", "rapport.pdf", "application/pdf", validPdfBytes());

        service.upload(file, "Titre", "Description", "admin@assrone.ch", DocumentVisibility.MEMBERS);

        try (var files = Files.list(uploadDir)) {
            assertThat(files.anyMatch(p -> p.getFileName().toString().startsWith("upload-"))).isFalse();
        }
    }

    @Test
    void uploadReussiPersisteLeDocumentEtEcritLeFichierFinal() throws IOException {
        stubSuccessfulSave();
        byte[] contenu = validPdfBytes();
        MockMultipartFile file = new MockMultipartFile("file", "rapport.pdf", "application/pdf", contenu);

        service.upload(file, "Rapport annuel", "Description", "admin@assrone.ch", DocumentVisibility.MEMBERS);

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        Document saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("Rapport annuel");
        assertThat(saved.getFileSize()).isEqualTo(contenu.length);
        assertThat(uploadDir.resolve(saved.getStoredFilename())).exists();
    }

    @Test
    void uploadCreeLeRepertoireSiAbsent() throws IOException {
        assertThat(Files.exists(uploadDir.resolve("sentinelle"))).isFalse();
        stubSuccessfulSave();
        MockMultipartFile file = new MockMultipartFile("file", "rapport.pdf", "application/pdf", validPdfBytes());

        service.upload(file, "Titre", "Description", "admin@assrone.ch", DocumentVisibility.MEMBERS);

        assertThat(Files.isDirectory(uploadDir)).isTrue();
    }

    // ===== Erreurs techniques =====

    @Test
    void uploadAvecEchecDeCreationDuTemporaireNAppelleJamaisLaSauvegardeDb() throws IOException {
        Path obstruction = uploadDir.resolve("obstruction");
        Files.write(obstruction, "un fichier régulier occupe l'emplacement attendu".getBytes());
        ReflectionTestUtils.setField(service, "uploadDir", obstruction.toString());
        MockMultipartFile file = new MockMultipartFile("file", "rapport.pdf", "application/pdf", validPdfBytes());

        assertThatThrownBy(() -> service.upload(file, "Titre", "Description", "admin@assrone.ch", DocumentVisibility.MEMBERS))
                .isInstanceOf(IOException.class)
                .isNotInstanceOf(InvalidDocumentException.class);

        verify(documentRepository, Mockito.never()).save(any());
    }

    @Test
    void uploadAvecEchecDeCopieNettoieLeTemporaire() throws IOException {
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getInputStream()).thenThrow(new IOException("échec de lecture simulé"));

        assertThatThrownBy(() -> service.upload(mockFile, "Titre", "Description", "admin@assrone.ch", DocumentVisibility.MEMBERS))
                .isInstanceOf(IOException.class)
                .isNotInstanceOf(InvalidDocumentException.class);

        verify(documentRepository, Mockito.never()).save(any());
        try (var files = Files.list(uploadDir)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void uploadAvecErreurRuntimeInattendueDeLInspecteurNettoieLeTemporaireEtRelanceLException() throws IOException {
        PdfDocumentInspector mockInspector = mock(PdfDocumentInspector.class);
        RuntimeException unexpected = new IllegalStateException("erreur interne inattendue de PDFBox");
        Mockito.doThrow(unexpected).when(mockInspector).inspect(any());
        DocumentService instance = serviceWithInspector(mockInspector);
        MockMultipartFile file = new MockMultipartFile("file", "rapport.pdf", "application/pdf", validPdfBytes());

        assertThatThrownBy(() -> instance.upload(file, "Titre", "Description", "admin@assrone.ch", DocumentVisibility.MEMBERS))
                .isSameAs(unexpected);

        verify(documentRepository, Mockito.never()).save(any());
        try (var files = Files.list(uploadDir)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void uploadAvecEchecDeSauvegardeSupprimeLeFichierFinal() throws IOException {
        RuntimeException dbFailure = new DataAccessResourceFailureException("base indisponible");
        when(documentRepository.save(any())).thenThrow(dbFailure);
        MockMultipartFile file = new MockMultipartFile("file", "rapport.pdf", "application/pdf", validPdfBytes());

        assertThatThrownBy(() -> service.upload(file, "Titre", "Description", "admin@assrone.ch", DocumentVisibility.MEMBERS))
                .isSameAs(dbFailure);

        try (var files = Files.list(uploadDir)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void uploadRelanceExactementLExceptionDbDOrigineMemeSiElleNEstPasDataAccess() throws IOException {
        RuntimeException dbFailure = new RuntimeException("erreur inattendue non liée à Spring Data");
        when(documentRepository.save(any())).thenThrow(dbFailure);
        MockMultipartFile file = new MockMultipartFile("file", "rapport.pdf", "application/pdf", validPdfBytes());

        assertThatThrownBy(() -> service.upload(file, "Titre", "Description", "admin@assrone.ch", DocumentVisibility.MEMBERS))
                .isSameAs(dbFailure);
    }

    // ===== Chargement (non-régression LOT 4b1) =====

    @Test
    void loadAsResourceAvecAncienNomPhysiqueAvecExtension() throws IOException {
        Files.write(uploadDir.resolve("ancien-nom.pdf"), "contenu".getBytes());
        Document document = existingDocument("ancien-nom.pdf");

        Resource resource = service.loadAsResource(document);

        assertThat(resource.exists()).isTrue();
    }

    @Test
    void loadAsResourceAvecNouveauNomPhysiqueUuidSansExtension() throws IOException {
        String uuid = "3f7b2b3a-1111-2222-3333-444455556666";
        Files.write(uploadDir.resolve(uuid), "contenu".getBytes());
        Document document = existingDocument(uuid);

        Resource resource = service.loadAsResource(document);

        assertThat(resource.exists()).isTrue();
    }

    @Test
    void loadAsResourceAvecStoredFilenameAbsoluLeveIllegalStateException() {
        Document document = existingDocument("/etc/passwd");

        assertThatThrownBy(() -> service.loadAsResource(document))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadAsResourceAvecStoredFilenameContenantDoublePointLeveIllegalStateException() {
        Document document = existingDocument("../../../etc/passwd");

        assertThatThrownBy(() -> service.loadAsResource(document))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getByIdAvecUnIdInexistantLeveResourceNotFound() {
        when(documentRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(42L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Document introuvable : 42");
    }

    // ===== Suppression (non-régression LOT 4b1) =====

    @Test
    void deleteAvecUnIdInexistantLeveResourceNotFound() {
        when(documentRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(42L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteNominaleSupprimeLaLigneDbEtLeFichierPhysique() throws IOException {
        Files.write(uploadDir.resolve("a-supprimer"), "contenu".getBytes());
        Document document = existingDocument("a-supprimer");
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        service.delete(1L);

        verify(documentRepository).delete(document);
        assertThat(uploadDir.resolve("a-supprimer")).doesNotExist();
    }

    @Test
    void deleteAvecEchecDbNeSupprimePasLeFichierPhysique() throws IOException {
        Files.write(uploadDir.resolve("a-conserver"), "contenu".getBytes());
        Document document = existingDocument("a-conserver");
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        RuntimeException dbFailure = new DataAccessResourceFailureException("base indisponible");
        Mockito.doThrow(dbFailure).when(documentRepository).delete(document);

        assertThatThrownBy(() -> service.delete(1L)).isSameAs(dbFailure);

        assertThat(uploadDir.resolve("a-conserver")).exists();
    }

    @Test
    void deleteAvecSuppressionDbReussiePuisEchecPhysiqueNeLeveAucuneException() throws IOException {
        Path obstruction = uploadDir.resolve("obstrue");
        Files.createDirectories(obstruction);
        Files.write(obstruction.resolve("non-vide.txt"), "contenu".getBytes());
        Document document = existingDocument("obstrue");
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        assertThatCode(() -> service.delete(1L)).doesNotThrowAnyException();

        verify(documentRepository).delete(document);
    }

    // ===== Visibilité =====

    private static Authentication authenticationWithRole(String role) {
        return new UsernamePasswordAuthenticationToken(
                "user@assrone.ch", null, List.of(new SimpleGrantedAuthority(role)));
    }

    @Test
    void uploadAvecVisibiliteNulleEstRejete() {
        MockMultipartFile file = new MockMultipartFile("file", "rapport.pdf", "application/pdf", new byte[]{1});

        assertThatThrownBy(() -> service.upload(file, "Titre", "Description", "admin@assrone.ch", null))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessage("La visibilité du document est obligatoire.");

        verify(documentRepository, Mockito.never()).save(any());
    }

    @Test
    void uploadPersisteLaVisibiliteDemandee() throws IOException {
        stubSuccessfulSave();
        MockMultipartFile file = new MockMultipartFile("file", "rapport.pdf", "application/pdf", validPdfBytes());

        service.upload(file, "Titre", "Description", "admin@assrone.ch", DocumentVisibility.ADMIN_ONLY);

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getVisibility()).isEqualTo(DocumentVisibility.ADMIN_ONLY);
    }

    @Test
    void getVisibleDocumentsPourUnMembreNeDemandeQueLaVisibiliteMembres() {
        when(documentRepository.findByVisibilityInOrderByUploadedAtDesc(List.of(DocumentVisibility.MEMBERS)))
                .thenReturn(List.of());

        service.getVisibleDocuments(authenticationWithRole("ROLE_USER"));

        verify(documentRepository).findByVisibilityInOrderByUploadedAtDesc(List.of(DocumentVisibility.MEMBERS));
    }

    @Test
    void getVisibleDocumentsPourUnAdminDemandeLesDeuxVisibilites() {
        when(documentRepository.findByVisibilityInOrderByUploadedAtDesc(
                List.of(DocumentVisibility.MEMBERS, DocumentVisibility.ADMIN_ONLY)))
                .thenReturn(List.of());

        service.getVisibleDocuments(authenticationWithRole("ROLE_ADMIN"));

        verify(documentRepository).findByVisibilityInOrderByUploadedAtDesc(
                List.of(DocumentVisibility.MEMBERS, DocumentVisibility.ADMIN_ONLY));
    }

    @Test
    void getVisibleByIdPourUnMembreSurUnDocumentAdminOnlyLeveResourceNotFound() {
        when(documentRepository.findByIdAndVisibilityIn(7L, List.of(DocumentVisibility.MEMBERS)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getVisibleById(7L, authenticationWithRole("ROLE_USER")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Document introuvable : 7");
    }

    @Test
    void getVisibleByIdPourUnAdminSurUnDocumentAdminOnlyReussit() {
        Document adminOnly = existingDocument("secret");
        adminOnly.setVisibility(DocumentVisibility.ADMIN_ONLY);
        when(documentRepository.findByIdAndVisibilityIn(
                7L, List.of(DocumentVisibility.MEMBERS, DocumentVisibility.ADMIN_ONLY)))
                .thenReturn(Optional.of(adminOnly));

        Document result = service.getVisibleById(7L, authenticationWithRole("ROLE_ADMIN"));

        assertThat(result).isSameAs(adminOnly);
    }

    @Test
    void getVisibleByIdAvecUnIdInexistantLeveResourceNotFound() {
        when(documentRepository.findByIdAndVisibilityIn(42L, List.of(DocumentVisibility.MEMBERS)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getVisibleById(42L, authenticationWithRole("ROLE_USER")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Document introuvable : 42");
    }
}
