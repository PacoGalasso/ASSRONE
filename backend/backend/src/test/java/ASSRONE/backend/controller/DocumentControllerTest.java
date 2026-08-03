package ASSRONE.backend.controller;

import ASSRONE.backend.exception.GlobalExceptionHandler;
import ASSRONE.backend.exception.ResourceNotFoundException;
import ASSRONE.backend.model.Document;
import ASSRONE.backend.service.DocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentControllerTest {

    private static final String DOWNLOAD_URL = "/api/documents/1/download";

    private DocumentService documentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        documentService = mock(DocumentService.class);
        DocumentController controller = new DocumentController(documentService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static Document documentWith(String originalFilename, String contentType) {
        return Document.builder()
                .id(1L)
                .title("Statuts")
                .description("Statuts de l'association")
                .originalFilename(originalFilename)
                .storedFilename("3f7b2b3a-1111-2222-3333-444455556666")
                .contentType(contentType)
                .fileSize(7L)
                .uploadedBy("admin@assrone.ch")
                .build();
    }

    private void stub(Document document) throws Exception {
        when(documentService.getById(1L)).thenReturn(document);
        when(documentService.loadAsResource(document)).thenReturn(new ByteArrayResource("contenu".getBytes()));
    }

    private String downloadAndGetDisposition() throws Exception {
        MvcResult result = mockMvc.perform(get(DOWNLOAD_URL))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getHeader("Content-Disposition");
    }

    // ===== Contrat HTTP de base =====

    @Test
    void leTelechargementRenvoieUnStatutNominal() throws Exception {
        stub(documentWith("rapport.pdf", "application/pdf"));

        mockMvc.perform(get(DOWNLOAD_URL)).andExpect(status().isOk());
    }

    @Test
    void leTelechargementRenvoieToujoursApplicationOctetStream() throws Exception {
        stub(documentWith("rapport.pdf", "application/pdf"));

        mockMvc.perform(get(DOWNLOAD_URL))
                .andExpect(header().string("Content-Type", "application/octet-stream"));
    }

    @Test
    void leTelechargementRenvoieToujoursAttachment() throws Exception {
        stub(documentWith("rapport.pdf", "application/pdf"));

        String disposition = downloadAndGetDisposition();

        assertThat(disposition).startsWith("attachment");
    }

    @Test
    void leTelechargementRenvoieToujoursNosniff() throws Exception {
        stub(documentWith("rapport.pdf", "application/pdf"));

        mockMvc.perform(get(DOWNLOAD_URL))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void leNomPhysiqueNEstJamaisExposeDansLEnTete() throws Exception {
        stub(documentWith("rapport.pdf", "application/pdf"));

        String disposition = downloadAndGetDisposition();

        assertThat(disposition).doesNotContain("3f7b2b3a-1111-2222-3333-444455556666");
    }

    // ===== Compatibilité des anciens contentType =====

    @Test
    void unAncienContentTypeValideEstIgnore() throws Exception {
        stub(documentWith("rapport.pdf", "application/pdf"));

        mockMvc.perform(get(DOWNLOAD_URL))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/octet-stream"));
    }

    @Test
    void unAncienContentTypeInvalideEstIgnoreSansErreur() throws Exception {
        stub(documentWith("rapport.pdf", "ceci n'est pas un type MIME"));

        mockMvc.perform(get(DOWNLOAD_URL))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/octet-stream"));
    }

    @Test
    void unAncienContentTypeVideEstIgnoreSansErreur() throws Exception {
        stub(documentWith("rapport.pdf", ""));

        mockMvc.perform(get(DOWNLOAD_URL))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/octet-stream"));
    }

    @Test
    void unAncienContentTypeNullEstIgnoreSansErreur() throws Exception {
        stub(documentWith("rapport.pdf", null));

        mockMvc.perform(get(DOWNLOAD_URL))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/octet-stream"));
    }

    // ===== Sanitisation du nom de téléchargement (preuve via header HTTP) =====

    @Test
    void unNomNormalEstConserveTelQuel() throws Exception {
        stub(documentWith("rapport.pdf", "application/pdf"));

        String disposition = downloadAndGetDisposition();

        assertThat(ContentDisposition.parse(disposition).getFilename()).isEqualTo("rapport.pdf");
    }

    @Test
    void unNomUnicodeAvecAccentsEstPreserve() throws Exception {
        stub(documentWith("rapport-général-été.pdf", "application/pdf"));

        String disposition = downloadAndGetDisposition();

        assertThat(ContentDisposition.parse(disposition).getFilename()).isEqualTo("rapport-général-été.pdf");
    }

    @Test
    void unGuillemetEstNeutralise() throws Exception {
        stub(documentWith("foo\"bar.pdf", "application/pdf"));

        String disposition = downloadAndGetDisposition();

        assertThat(ContentDisposition.parse(disposition).getFilename()).isEqualTo("foo_bar.pdf");
    }

    @Test
    void unCarriageReturnEstNeutralise() throws Exception {
        stub(documentWith("foo\rbar.pdf", "application/pdf"));

        String disposition = downloadAndGetDisposition();

        assertThat(ContentDisposition.parse(disposition).getFilename()).isEqualTo("foo_bar.pdf");
        assertThat(disposition).doesNotContain("\r").doesNotContain("\n");
    }

    @Test
    void unLineFeedEstNeutralise() throws Exception {
        stub(documentWith("foo\nbar.pdf", "application/pdf"));

        String disposition = downloadAndGetDisposition();

        assertThat(ContentDisposition.parse(disposition).getFilename()).isEqualTo("foo_bar.pdf");
        assertThat(disposition).doesNotContain("\r").doesNotContain("\n");
    }

    @Test
    void unCrlfEstNeutraliseEtNInjectePasDeSecondHeader() throws Exception {
        stub(documentWith("foo\r\nX-Injected: evil", "application/pdf"));

        MvcResult result = mockMvc.perform(get(DOWNLOAD_URL))
                .andExpect(status().isOk())
                .andReturn();
        String disposition = result.getResponse().getHeader("Content-Disposition");

        assertThat(disposition).doesNotContain("\r").doesNotContain("\n");
        assertThat(result.getResponse().getHeader("X-Injected")).isNull();
        assertThat(ContentDisposition.parse(disposition).getFilename()).isEqualTo("foo__X-Injected: evil");
    }

    @Test
    void unSlashEstNeutralise() throws Exception {
        stub(documentWith("a/b.pdf", "application/pdf"));

        String disposition = downloadAndGetDisposition();

        assertThat(ContentDisposition.parse(disposition).getFilename()).isEqualTo("a_b.pdf");
    }

    @Test
    void unBackslashEstNeutralise() throws Exception {
        stub(documentWith("a\\b.pdf", "application/pdf"));

        String disposition = downloadAndGetDisposition();

        assertThat(ContentDisposition.parse(disposition).getFilename()).isEqualTo("a_b.pdf");
    }

    @Test
    void unNomNullUtiliseLeRepliGenerique() throws Exception {
        stub(documentWith(null, "application/pdf"));

        String disposition = downloadAndGetDisposition();

        assertThat(ContentDisposition.parse(disposition).getFilename()).isEqualTo("document");
    }

    @Test
    void unNomVideUtiliseLeRepliGenerique() throws Exception {
        stub(documentWith("", "application/pdf"));

        String disposition = downloadAndGetDisposition();

        assertThat(ContentDisposition.parse(disposition).getFilename()).isEqualTo("document");
    }

    @Test
    void unNomBlancUtiliseLeRepliGenerique() throws Exception {
        stub(documentWith("   ", "application/pdf"));

        String disposition = downloadAndGetDisposition();

        assertThat(ContentDisposition.parse(disposition).getFilename()).isEqualTo("document");
    }

    @Test
    void unNomEgalAUnPointUtiliseLeRepliGenerique() throws Exception {
        stub(documentWith(".", "application/pdf"));

        String disposition = downloadAndGetDisposition();

        assertThat(ContentDisposition.parse(disposition).getFilename()).isEqualTo("document");
    }

    @Test
    void unNomEgalADeuxPointsUtiliseLeRepliGenerique() throws Exception {
        stub(documentWith("..", "application/pdf"));

        String disposition = downloadAndGetDisposition();

        assertThat(ContentDisposition.parse(disposition).getFilename()).isEqualTo("document");
    }

    @Test
    void unNomTresLongEstTronqueA200PointsDeCode() throws Exception {
        String longName = "a".repeat(250) + ".pdf";
        stub(documentWith(longName, "application/pdf"));

        String disposition = downloadAndGetDisposition();

        String filename = ContentDisposition.parse(disposition).getFilename();
        assertThat(filename).isNotNull();
        assertThat(filename.codePointCount(0, filename.length())).isEqualTo(200);
    }

    @Test
    void unNomAvecCaracteresHorsBmpEstTronqueSansDecoupeInvalide() throws Exception {
        String emojiName = "😀".repeat(210);
        stub(documentWith(emojiName, "application/pdf"));

        String disposition = downloadAndGetDisposition();

        String filename = ContentDisposition.parse(disposition).getFilename();
        assertThat(filename).isNotNull();
        assertThat(filename.codePointCount(0, filename.length())).isEqualTo(200);
        assertThat(filename.length()).isEqualTo(400);
    }

    @Test
    void aucunCaractereDeControleBrutDansLeHeaderFinal() throws Exception {
        stub(documentWith("abc.pdf", "application/pdf"));

        String disposition = downloadAndGetDisposition();

        for (char c : disposition.toCharArray()) {
            assertThat(Character.isISOControl(c)).isFalse();
        }
    }

    // ===== Document inexistant =====

    @Test
    void unDocumentInexistantRenvoie404() throws Exception {
        when(documentService.getById(99L)).thenThrow(new ResourceNotFoundException("Document introuvable : 99"));

        mockMvc.perform(get("/api/documents/99/download"))
                .andExpect(status().isNotFound());
    }
}
