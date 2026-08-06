package ASSRONE.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Coarse, early @Size bounds only — the authoritative check is
 * PasswordPolicy#validate (byte-length aware, see there), which
 * PasswordResetService always calls regardless of what passed here.
 */
@Data
public class ResetPasswordRequest {

    @NotBlank(message = "Le token est obligatoire")
    private String token;

    @NotBlank(message = "Le nouveau mot de passe est obligatoire")
    @Size(min = 8, max = 200, message = "Le nouveau mot de passe doit contenir entre 8 et 200 caractères")
    private String newPassword;
}
