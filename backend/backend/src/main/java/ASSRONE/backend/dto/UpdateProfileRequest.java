package ASSRONE.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileRequest {

    @NotBlank(message = "Le nom d'utilisateur est obligatoire")
    private String username;

    private String firstName;
    private String lastName;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format d'email invalide")
    private String email;

    // Required only when the submitted email differs from the account's
    // current one — see UserProfileService#updateProfile. Left blank for a
    // no-op or non-email profile update.
    private String currentPassword;
}
