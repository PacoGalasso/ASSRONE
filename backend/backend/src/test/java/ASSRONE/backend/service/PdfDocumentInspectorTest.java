package ASSRONE.backend.service;

import ASSRONE.backend.exception.InvalidDocumentException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfDocumentInspectorTest {

    private final PdfDocumentInspector inspector = new PdfDocumentInspector();

    @TempDir
    private Path tempDir;

    private Path validPdf(String name) throws IOException {
        Path path = tempDir.resolve(name);
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(path.toFile());
        }
        return path;
    }

    private Path encryptedPdf(String name, String ownerPassword, String userPassword) throws IOException {
        Path path = tempDir.resolve(name);
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            StandardProtectionPolicy policy =
                    new StandardProtectionPolicy(ownerPassword, userPassword, new AccessPermission());
            document.protect(policy);
            document.save(path.toFile());
        }
        return path;
    }

    @Test
    void unFichierNulEstRejete() {
        assertThatThrownBy(() -> inspector.inspect(null))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessage("Le document est vide.");
    }

    @Test
    void unCheminInexistantResteUneErreurTechnique() {
        Path missing = tempDir.resolve("absent.pdf");

        assertThatThrownBy(() -> inspector.inspect(missing))
                .isInstanceOf(IOException.class)
                .isNotInstanceOf(InvalidDocumentException.class);
    }

    @Test
    void unCheminNonRegulierResteUneErreurTechnique() {
        assertThatThrownBy(() -> inspector.inspect(tempDir))
                .isInstanceOf(IOException.class)
                .isNotInstanceOf(InvalidDocumentException.class);
    }

    @Test
    void unFichierVideEstRejete() throws IOException {
        Path empty = tempDir.resolve("empty.pdf");
        Files.write(empty, new byte[0]);

        assertThatThrownBy(() -> inspector.inspect(empty))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessage("Le document est vide.");
    }

    @Test
    void unTexteRenommeEnPdfEstRejete() throws IOException {
        Path fake = tempDir.resolve("fake.pdf");
        Files.write(fake, "ceci n'est pas un PDF, juste du texte.".getBytes());

        assertThatThrownBy(() -> inspector.inspect(fake))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessage("Le document doit être un fichier PDF valide.");
    }

    @Test
    void unContenuCommencantParLaSignaturePdfMaisStructureInvalideEstRejete() throws IOException {
        Path fake = tempDir.resolve("fake-header.pdf");
        Files.write(fake, "%PDF-1.7\nceci n'est pas une structure PDF valide.".getBytes());

        assertThatThrownBy(() -> inspector.inspect(fake))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessage("Le document doit être un fichier PDF valide.");
    }

    @Test
    void unContenuContenantEofMaisStructureInvalideEstRejete() throws IOException {
        Path fake = tempDir.resolve("fake-eof.pdf");
        Files.write(fake, "contenu arbitraire\n%%EOF".getBytes());

        assertThatThrownBy(() -> inspector.inspect(fake))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessage("Le document doit être un fichier PDF valide.");
    }

    @Test
    void unPdfTronqueEstRejete() throws IOException {
        Path source = validPdf("source-for-truncation.pdf");
        byte[] fullContent = Files.readAllBytes(source);
        byte[] truncatedContent = Arrays.copyOf(fullContent, 10);
        Path truncated = tempDir.resolve("truncated.pdf");
        Files.write(truncated, truncatedContent);

        assertThatThrownBy(() -> inspector.inspect(truncated))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessage("Le document doit être un fichier PDF valide.");
    }

    @Test
    void unPdfMinimalValideEstAccepte() throws IOException {
        Path valid = validPdf("valid.pdf");

        assertThatCode(() -> inspector.inspect(valid)).doesNotThrowAnyException();
    }

    @Test
    void unPdfNonChiffreEstAccepte() throws IOException {
        Path valid = validPdf("non-chiffre.pdf");

        assertThatCode(() -> inspector.inspect(valid)).doesNotThrowAnyException();
    }

    @Test
    void unPdfChiffreAvecMotDePasseUtilisateurRequisEstRejete() throws IOException {
        Path encrypted = encryptedPdf("encrypted-user-password.pdf", "proprietaire", "utilisateur");

        assertThatThrownBy(() -> inspector.inspect(encrypted))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessage("Les documents PDF protégés par mot de passe ne sont pas acceptés.");
    }

    @Test
    void unPdfAvecMotDePasseProprietaireEtMotDePasseUtilisateurVideEstRejeteViaIsEncrypted() throws IOException {
        Path encrypted = encryptedPdf("encrypted-owner-only.pdf", "proprietaire", "");

        assertThatThrownBy(() -> inspector.inspect(encrypted))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessage("Les documents PDF protégés par mot de passe ne sont pas acceptés.");
    }

    @Test
    void leFichierSourceResteSupprimableImmediatementApresInspectionReussie() throws IOException {
        Path valid = validPdf("supprimable-succes.pdf");

        inspector.inspect(valid);

        assertThatCode(() -> Files.delete(valid)).doesNotThrowAnyException();
    }

    @Test
    void leFichierSourceResteSupprimableImmediatementApresRejet() throws IOException {
        Path fake = tempDir.resolve("supprimable-rejet.pdf");
        Files.write(fake, "pas un pdf".getBytes());

        assertThatThrownBy(() -> inspector.inspect(fake)).isInstanceOf(InvalidDocumentException.class);

        assertThatCode(() -> Files.delete(fake)).doesNotThrowAnyException();
    }
}
