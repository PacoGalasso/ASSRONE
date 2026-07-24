package ASSRONE.backend.dto;

import ASSRONE.backend.model.MembershipType;
import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMembershipApplicationRequest {

    @NotBlank(message = "Le nom est obligatoire")
    private String fullName;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format d'email invalide")
    private String email;

    private String phone;

    @NotNull(message = "Le type de membre est obligatoire")
    private MembershipType membershipType;

    @Size(max = 1000)
    private String message;

    @AssertTrue(message = "Vous devez accepter la charte pour soumettre votre demande")
    private boolean charterAccepted;
}
