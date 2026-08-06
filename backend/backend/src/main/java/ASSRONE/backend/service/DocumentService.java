package ASSRONE.backend.service;

import ASSRONE.backend.audit.SecurityAuditService;
import ASSRONE.backend.audit.SecurityEventResult;
import ASSRONE.backend.audit.SecurityEventType;
import ASSRONE.backend.dto.DocumentDto;
import ASSRONE.backend.exception.InvalidDocumentException;
import ASSRONE.backend.exception.ResourceNotFoundException;
import ASSRONE.backend.mapper.DocumentMapper;
import ASSRONE.backend.model.Document;
import ASSRONE.backend.model.DocumentVisibility;
import ASSRONE.backend.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentMapper documentMapper;
    private final PdfDocumentInspector pdfDocumentInspector;
    private final SecurityAuditService securityAuditService;

    @Value("${app.upload-dir}")
    private String uploadDir;

    public DocumentDto upload(MultipartFile file, String title, String description, String uploadedBy,
                               DocumentVisibility visibility) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new InvalidDocumentException("Le document est vide.");
        }
        if (visibility == null) {
            throw new InvalidDocumentException("La visibilité du document est obligatoire.");
        }

        Path root = resolveStorageRoot();
        if (!Files.exists(root)) {
            Files.createDirectories(root);
        }

        Path temporaryFile = Files.createTempFile(root, "upload-", ".tmp");
        Path target;
        try {
            Files.copy(file.getInputStream(), temporaryFile, StandardCopyOption.REPLACE_EXISTING);
            pdfDocumentInspector.inspect(temporaryFile);

            String storedFilename = UUID.randomUUID().toString();
            target = resolveStoragePath(storedFilename);
            moveIntoPlace(temporaryFile, target);
            temporaryFile = null;
        } finally {
            deleteQuietly(temporaryFile);
        }

        Document document = Document.builder()
                .title(title)
                .description(description)
                .originalFilename(file.getOriginalFilename())
                .storedFilename(target.getFileName().toString())
                .contentType(MediaType.APPLICATION_PDF_VALUE)
                .fileSize(file.getSize())
                .uploadedBy(uploadedBy)
                .visibility(visibility)
                .build();

        Document saved;
        try {
            saved = documentRepository.save(document);
        } catch (RuntimeException ex) {
            deleteQuietly(target);
            throw ex;
        }

        securityAuditService.record(SecurityEventType.DOCUMENT_UPLOADED, SecurityEventResult.SUCCESS,
                SecurityAuditService.maskEmail(uploadedBy), null, "document", String.valueOf(saved.getId()), null);
        return documentMapper.toDto(saved);
    }

    private void moveIntoPlace(Path temporaryFile, Path target) throws IOException {
        try {
            Files.move(temporaryFile, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Path root = resolveStorageRoot();
            if (!temporaryFile.startsWith(root) || !target.startsWith(root)) {
                throw ex;
            }
            Files.move(temporaryFile, target);
        }
    }

    public List<DocumentDto> getVisibleDocuments(Authentication authentication) {
        return documentRepository.findByVisibilityInOrderByUploadedAtDesc(visibilitiesFor(authentication))
                .stream()
                .map(documentMapper::toDto)
                .toList();
    }

    public Document getVisibleById(Long id, Authentication authentication) {
        return documentRepository.findByIdAndVisibilityIn(id, visibilitiesFor(authentication))
                .orElseThrow(() -> new ResourceNotFoundException("Document introuvable : " + id));
    }

    private List<DocumentVisibility> visibilitiesFor(Authentication authentication) {
        return isAdmin(authentication)
                ? List.of(DocumentVisibility.MEMBERS, DocumentVisibility.ADMIN_ONLY)
                : List.of(DocumentVisibility.MEMBERS);
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
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

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String actorId = authentication != null ? SecurityAuditService.maskEmail(authentication.getName()) : "-";
        securityAuditService.record(SecurityEventType.DOCUMENT_DELETED, SecurityEventResult.SUCCESS,
                actorId, null, "document", String.valueOf(id), null);
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
