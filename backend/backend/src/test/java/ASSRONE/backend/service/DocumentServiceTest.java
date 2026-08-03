package ASSRONE.backend.service;

import ASSRONE.backend.dto.DocumentDto;
import ASSRONE.backend.exception.ResourceNotFoundException;
import ASSRONE.backend.mapper.DocumentMapper;
import ASSRONE.backend.model.Document;
import ASSRONE.backend.repository.DocumentRepository;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
        service = new DocumentService(documentRepository, documentMapper);
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

    // ===== Upload =====

    @Test
    void uploadGenereUnNomPhysiqueUuidSansExtension() throws IOException {
        stubSuccessfulSave();
        MockMultipartFile file = new MockMultipartFile("file", "rapport.pdf", "application/pdf", "contenu".getBytes());

        service.upload(file, "Titre", "Description", "admin@assrone.ch");

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getStoredFilename())
                .matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    }

    @Test
    void uploadIgnoreLeNomOriginalPourLeNomPhysique() throws IOException {
        stubSuccessfulSave();
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../../etc/passwd.pdf", "application/pdf", "contenu".getBytes());

        service.upload(file, "Titre", "Description", "admin@assrone.ch");

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getStoredFilename())
                .doesNotContain("etc")
                .doesNotContain("passwd")
                .doesNotContain("..")
                .doesNotContain(".pdf");
    }

    @Test
    void uploadIgnoreLeTypeMimeMultipartFalsifiePourLaPersistance() throws IOException {
        stubSuccessfulSave();
        MockMultipartFile file = new MockMultipartFile("file", "rapport.pdf", "text/html", "contenu".getBytes());

        service.upload(file, "Titre", "Description", "admin@assrone.ch");

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    @Test
    void uploadPersisteExactementApplicationOctetStream() throws IOException {
        stubSuccessfulSave();
        MockMultipartFile file = new MockMultipartFile("file", "rapport.pdf", "application/pdf", "contenu".getBytes());

        service.upload(file, "Titre", "Description", "admin@assrone.ch");

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getContentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void uploadEcritLeFichierSousLaRacineAutorisee() throws IOException {
        stubSuccessfulSave();
        MockMultipartFile file = new MockMultipartFile("file", "rapport.pdf", "application/pdf", "contenu".getBytes());

        service.upload(file, "Titre", "Description", "admin@assrone.ch");

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        Path stored = uploadDir.resolve(captor.getValue().getStoredFilename());
        assertThat(stored).exists();
        assertThat(stored.normalize()).startsWith(uploadDir.normalize());
    }

    @Test
    void uploadCreeLeRepertoireSiAbsent() throws IOException {
        assertThat(Files.exists(uploadDir.resolve("sentinelle"))).isFalse();
        stubSuccessfulSave();
        MockMultipartFile file = new MockMultipartFile("file", "rapport.pdf", "application/pdf", "contenu".getBytes());

        service.upload(file, "Titre", "Description", "admin@assrone.ch");

        assertThat(Files.isDirectory(uploadDir)).isTrue();
    }

    @Test
    void uploadAccepteUnContenuArbitraireNonPdf() throws IOException {
        stubSuccessfulSave();
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "ceci n'est pas un PDF".getBytes());

        assertThatCode(() -> service.upload(file, "Titre", "Description", "admin@assrone.ch"))
                .doesNotThrowAnyException();
    }

    @Test
    void uploadAccepteUnFichierVide() throws IOException {
        stubSuccessfulSave();
        MockMultipartFile file = new MockMultipartFile("file", "vide.pdf", "application/pdf", new byte[0]);

        assertThatCode(() -> service.upload(file, "Titre", "Description", "admin@assrone.ch"))
                .doesNotThrowAnyException();
        verify(documentRepository).save(any());
    }

    @Test
    void uploadReussiPersisteLeDocumentEtEcritLeFichier() throws IOException {
        stubSuccessfulSave();
        byte[] contenu = "contenu du rapport".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "rapport.pdf", "application/pdf", contenu);

        service.upload(file, "Rapport annuel", "Description", "admin@assrone.ch");

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        Document saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("Rapport annuel");
        assertThat(saved.getOriginalFilename()).isEqualTo("rapport.pdf");
        assertThat(saved.getFileSize()).isEqualTo(contenu.length);
        assertThat(uploadDir.resolve(saved.getStoredFilename())).exists();
    }

    @Test
    void uploadAvecEchecDeSauvegardeSupprimeLeFichier() throws IOException {
        RuntimeException dbFailure = new DataAccessResourceFailureException("base indisponible");
        when(documentRepository.save(any())).thenThrow(dbFailure);
        MockMultipartFile file = new MockMultipartFile("file", "rapport.pdf", "application/pdf", "contenu".getBytes());

        assertThatThrownBy(() -> service.upload(file, "Titre", "Description", "admin@assrone.ch"))
                .isSameAs(dbFailure);

        try (var files = Files.list(uploadDir)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void uploadRelanceExactementLExceptionDOrigineMemeSiElleNEstPasDataAccess() throws IOException {
        RuntimeException dbFailure = new RuntimeException("erreur inattendue non liée à Spring Data");
        when(documentRepository.save(any())).thenThrow(dbFailure);
        MockMultipartFile file = new MockMultipartFile("file", "rapport.pdf", "application/pdf", "contenu".getBytes());

        assertThatThrownBy(() -> service.upload(file, "Titre", "Description", "admin@assrone.ch"))
                .isSameAs(dbFailure);
    }

    @Test
    void uploadAvecErreurDisqueNAppelleJamaisLaSauvegardeDb() throws IOException {
        Path obstruction = uploadDir.resolve("obstruction");
        Files.write(obstruction, "un fichier régulier occupe l'emplacement attendu".getBytes());
        ReflectionTestUtils.setField(service, "uploadDir", obstruction.toString());
        MockMultipartFile file = new MockMultipartFile("file", "rapport.pdf", "application/pdf", "contenu".getBytes());

        assertThatThrownBy(() -> service.upload(file, "Titre", "Description", "admin@assrone.ch"))
                .isInstanceOf(IOException.class);

        verify(documentRepository, Mockito.never()).save(any());
    }

    @Test
    void uploadEcritUnContenuIdentiqueAuFluxRecu() throws IOException {
        stubSuccessfulSave();
        byte[] contenu = "contenu exact du document".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "rapport.pdf", "application/pdf", contenu);

        service.upload(file, "Titre", "Description", "admin@assrone.ch");

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        Path stored = uploadDir.resolve(captor.getValue().getStoredFilename());
        assertThat(Files.readAllBytes(stored)).isEqualTo(contenu);
    }

    // ===== Chargement =====

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

    // ===== Suppression =====

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
}
