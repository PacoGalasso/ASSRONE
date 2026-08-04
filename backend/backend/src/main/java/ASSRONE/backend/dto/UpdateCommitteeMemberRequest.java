package ASSRONE.backend.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCommitteeMemberRequest {

    @NotBlank(message = "Le prénom est obligatoire")
    @Size(min = 2, max = 80, message = "Le prénom doit contenir entre 2 et 80 caractères")
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    @Size(min = 2, max = 80, message = "Le nom doit contenir entre 2 et 80 caractères")
    private String lastName;

    @NotBlank(message = "La fonction est obligatoire")
    @Size(min = 2, max = 100, message = "La fonction doit contenir entre 2 et 100 caractères")
    private String role;

    @Size(max = 1200, message = "La description ne doit pas dépasser 1200 caractères")
    private String description;

    @NotNull(message = "L'ordre d'affichage est obligatoire")
    @Min(value = 0, message = "L'ordre d'affichage doit être positif ou nul")
    private Integer displayOrder;

    @NotNull(message = "La visibilité est obligatoire")
    private Boolean active;
}
