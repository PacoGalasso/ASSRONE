package ASSRONE.backend.service;

import ASSRONE.backend.dto.DocumentDto;
import ASSRONE.backend.exception.ResourceNotFoundException;
import ASSRONE.backend.mapper.DocumentMapper;
import ASSRONE.backend.model.Document;
import ASSRONE.backend.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentMapper documentMapper;

    @Value("${app.upload-dir}")
    private String uploadDir;

    public DocumentDto upload(MultipartFile file, String title, String description, String uploadedBy) throws IOException {
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String extension = "";
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }
        String storedFilename = UUID.randomUUID() + extension;

        Path targetPath = uploadPath.resolve(storedFilename);
        Files.copy(file.getInputStream(), targetPath);

        Document document = Document.builder()
                .title(title)
                .description(description)
                .originalFilename(originalFilename)
                .storedFilename(storedFilename)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .uploadedBy(uploadedBy)
                .build();

        Document saved = documentRepository.save(document);
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
        Path filePath = Paths.get(uploadDir).resolve(document.getStoredFilename());
        return new UrlResource(filePath.toUri());
    }

    public void delete(Long id) throws IOException {
        Document document = getById(id);
        Files.deleteIfExists(Paths.get(uploadDir).resolve(document.getStoredFilename()));
        documentRepository.delete(document);
    }
}
