package ASSRONE.backend.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateEventRequest {

    @NotBlank(message = "Le titre est obligatoire")
    private String title;

    @NotBlank(message = "La description est obligatoire")
    @Size(max = 1000)
    private String description;

    @NotBlank(message = "Le type d'événement est obligatoire")
    @Size(min = 2, max = 80, message = "Le type d'événement doit contenir entre 2 et 80 caractères")
    private String type;

    @NotNull(message = "La date est obligatoire")
    @FutureOrPresent(message = "La date doit être aujourd'hui ou dans le futur")
    private LocalDate eventDate;

    @NotNull(message = "L'heure de début est obligatoire")
    private LocalTime startTime;

    @NotNull(message = "L'heure de fin est obligatoire")
    private LocalTime endTime;

    @NotBlank(message = "Le lieu est obligatoire")
    private String location;

    @NotNull(message = "Le nombre de places est obligatoire")
    @Min(value = 1, message = "Il faut au moins 1 place")
    private Integer maxParticipants;
}
