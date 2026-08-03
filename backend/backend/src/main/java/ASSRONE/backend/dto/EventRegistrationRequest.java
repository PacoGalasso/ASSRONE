package ASSRONE.backend.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventRegistrationRequest {

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 150)
    private String fullName;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format d'email invalide")
    @Size(max = 254)
    private String email;
}
