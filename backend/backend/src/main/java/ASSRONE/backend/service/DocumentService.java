package ASSRONE.backend.service;

import ASSRONE.backend.dto.DocumentDto;
import ASSRONE.backend.exception.ResourceNotFoundException;
import ASSRONE.backend.mapper.DocumentMapper;
import ASSRONE.backend.model.Document;
import ASSRONE.backend.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentMapper documentMapper;

    @Value("${app.upload-dir}")
    private String uploadDir;

    public DocumentDto upload(MultipartFile file, String title, String description, String uploadedBy) throws IOException {
        Path root = resolveStorageRoot();
        if (!Files.exists(root)) {
            Files.createDirectories(root);
        }

        String storedFilename = UUID.randomUUID().toString();
        Path target = resolveStoragePath(storedFilename);
        Files.copy(file.getInputStream(), target);

        Document document = Document.builder()
                .title(title)
                .description(description)
                .originalFilename(file.getOriginalFilename())
                .storedFilename(storedFilename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .fileSize(file.getSize())
                .uploadedBy(uploadedBy)
                .build();

        Document saved;
        try {
            saved = documentRepository.save(document);
        } catch (RuntimeException ex) {
            deleteQuietly(target);
            throw ex;
        }

        return documentMapper.toDto(saved);
    }

    public List<DocumentDto> getAll() {
        return documentRepository.findAllByOrderByUploadedAtDesc()
                .stream()
                .map(documentMapper::toDto)
                .toList();
    }

    public Document getById(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document introuvable : " + id));
    }

    public Resource loadAsResource(Document document) throws MalformedURLException {
        Path path = resolveStoragePath(document.getStoredFilename());
        return new UrlResource(path.toUri());
    }

    public void delete(Long id) {
        Document document = getById(id);
        Path path = resolveStoragePath(document.getStoredFilename());
        documentRepository.delete(document);
        deleteQuietly(path);
    }

    private Path resolveStorageRoot() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    private Path resolveStoragePath(String storedFilename) {
        Path root = resolveStorageRoot();
        Path target = root.resolve(storedFilename).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalStateException("Chemin de document résolu hors du répertoire autorisé.");
        }
        return target;
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Échec du nettoyage d'un fichier de document.");
        }
    }
}
