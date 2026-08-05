package ASSRONE.backend.exception;

import ASSRONE.backend.security.RefreshCookieFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(new RefreshCookieFactory("refresh_token", false, "Lax", "/auth"));

    @Test
    void resourceNotFoundRetourne404AvecUniquementLaCleError() {
        ResponseEntity<Map<String, String>> response =
                handler.handleResourceNotFound(new ResourceNotFoundException("Document introuvable : 42"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsExactly(Map.entry("error", "Document introuvable : 42"));
    }

    @Test
    void invalidPasswordRetourne400AvecUniquementLaCleError() {
        ResponseEntity<Map<String, String>> response =
                handler.handleInvalidPassword(new InvalidPasswordException("Le mot de passe actuel est incorrect"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsExactly(Map.entry("error", "Le mot de passe actuel est incorrect"));
    }

    @Test
    void invalidAvatarRetourne400AvecUniquementLaCleError() {
        ResponseEntity<Map<String, String>> response =
                handler.handleInvalidAvatar(new InvalidAvatarException("Seuls les formats JPEG et PNG sont acceptés."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsExactly(Map.entry("error", "Seuls les formats JPEG et PNG sont acceptés."));
    }

    @Test
    void invalidDocumentRetourne400AvecUniquementLaCleError() {
        ResponseEntity<Map<String, String>> response =
                handler.handleInvalidDocument(new InvalidDocumentException("Le document doit être un fichier PDF valide."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsExactly(Map.entry("error", "Le document doit être un fichier PDF valide."));
    }

    @Test
    void eventRegistrationAlreadyExistsRetourne409AvecUniquementLaCleError() {
        ResponseEntity<Map<String, String>> response =
                handler.handleEventRegistrationAlreadyExists(
                        new EventRegistrationAlreadyExistsException("Vous êtes déjà inscrit à cet événement."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsExactly(Map.entry("error", "Vous êtes déjà inscrit à cet événement."));
    }
}
