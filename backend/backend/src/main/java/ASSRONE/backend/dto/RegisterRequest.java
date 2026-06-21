package ASSRONE.backend.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class RegisterRequest {
    @NotBlank(message = "Username required")
    @Size(min = 3, max = 50)
    private String username;

    @NotBlank(message = "Email required")
    @Email
    private String email;

    @NotBlank(message = "First name required")
    private String firstName;

    @NotBlank(message = "Last name required")
    private String lastName;

    @NotBlank(message = "Password required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

}
