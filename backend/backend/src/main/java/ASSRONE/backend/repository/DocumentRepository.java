package ASSRONE.backend.repository;

import ASSRONE.backend.model.Document;
import ASSRONE.backend.model.DocumentVisibility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findAllByOrderByUploadedAtDesc();

    List<Document> findByVisibilityInOrderByUploadedAtDesc(List<DocumentVisibility> visibilities);

    Optional<Document> findByIdAndVisibilityIn(Long id, List<DocumentVisibility> visibilities);
}