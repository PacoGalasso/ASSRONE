package ASSRONE.backend.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void resourceNotFoundRetourne404AvecUniquementLaCleError() {
        ResponseEntity<Map<String, String>> response =
                handler.handleResourceNotFound(new ResourceNotFoundException("Document introuvable : 42"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsExactly(Map.entry("error", "Document introuvable : 42"));
    }
}
