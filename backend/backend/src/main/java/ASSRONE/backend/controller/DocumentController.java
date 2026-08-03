package ASSRONE.backend.controller;

import ASSRONE.backend.dto.DocumentDto;
import ASSRONE.backend.model.Document;
import ASSRONE.backend.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private static final String FALLBACK_DOWNLOAD_FILENAME = "document";
    private static final int MAX_DOWNLOAD_FILENAME_CODEPOINTS = 200;
    private static final String CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options";
    private static final String NOSNIFF = "nosniff";

    private final DocumentService documentService;

    @GetMapping
    public List<DocumentDto> getAll() {
        return documentService.getAll();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentDto> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            Authentication authentication) throws IOException {

        DocumentDto created = documentService.upload(file, title, description, authentication.getName());
        return ResponseEntity.status(201).body(created);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) throws IOException {
        Document document = documentService.getById(id);
        Resource resource = documentService.loadAsResource(document);

        String downloadFilename = sanitizeDownloadFilename(document.getOriginalFilename());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(downloadFilename, StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(CONTENT_TYPE_OPTIONS_HEADER, NOSNIFF)
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private String sanitizeDownloadFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return FALLBACK_DOWNLOAD_FILENAME;
        }

        String trimmed = originalFilename.trim();
        StringBuilder sanitized = new StringBuilder(trimmed.length());
        trimmed.codePoints().forEach(codePoint -> {
            if (Character.isISOControl(codePoint) || codePoint == '/' || codePoint == '\\' || codePoint == '"') {
                sanitized.append('_');
            } else {
                sanitized.appendCodePoint(codePoint);
            }
        });

        String result = sanitized.toString();
        if (result.isBlank() || result.equals(".") || result.equals("..")) {
            return FALLBACK_DOWNLOAD_FILENAME;
        }

        return truncateByCodePoint(result, MAX_DOWNLOAD_FILENAME_CODEPOINTS);
    }

    private String truncateByCodePoint(String value, int maxCodePoints) {
        int codePointCount = value.codePointCount(0, value.length());
        if (codePointCount <= maxCodePoints) {
            return value;
        }
        int endIndex = value.offsetByCodePoints(0, maxCodePoints);
        return value.substring(0, endIndex);
    }
}