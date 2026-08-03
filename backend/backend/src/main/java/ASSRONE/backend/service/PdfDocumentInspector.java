package ASSRONE.backend.service;

import ASSRONE.backend.exception.InvalidDocumentException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class PdfDocumentInspector {

    public void inspect(Path pdfFile) throws IOException {
        if (pdfFile == null) {
            throw new InvalidDocumentException("Le document est vide.");
        }
        if (!Files.isRegularFile(pdfFile) || !Files.isReadable(pdfFile)) {
            throw new FileSystemException(pdfFile.toString(), null, "Chemin de document inattendu.");
        }
        if (Files.size(pdfFile) == 0) {
            throw new InvalidDocumentException("Le document est vide.");
        }

        try (PDDocument document = Loader.loadPDF(
                pdfFile.toFile(), "", IOUtils.createTempFileOnlyStreamCache())) {
            if (document.isEncrypted()) {
                throw new InvalidDocumentException(
                        "Les documents PDF protégés par mot de passe ne sont pas acceptés.");
            }
            document.getNumberOfPages();
        } catch (InvalidPasswordException ex) {
            throw new InvalidDocumentException(
                    "Les documents PDF protégés par mot de passe ne sont pas acceptés.");
        } catch (IOException ex) {
            throw new InvalidDocumentException("Le document doit être un fichier PDF valide.");
        }
    }
}
