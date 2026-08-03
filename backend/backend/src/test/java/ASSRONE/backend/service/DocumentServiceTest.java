package ASSRONE.backend.service;

import ASSRONE.backend.exception.ResourceNotFoundException;
import ASSRONE.backend.mapper.DocumentMapper;
import ASSRONE.backend.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentMapper documentMapper;

    @Test
    void getByIdAvecUnIdInexistantLeveResourceNotFound() {
        DocumentService service = new DocumentService(documentRepository, documentMapper);
        when(documentRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(42L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Document introuvable : 42");
    }
}
