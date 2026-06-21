package ASSRONE.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
@Data
public class ChangePasswordRequest {
    @NotBlank(message = "Old password required")
    private String oldPassword;

    @NotBlank(message = "New password required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String newPassword;
}
