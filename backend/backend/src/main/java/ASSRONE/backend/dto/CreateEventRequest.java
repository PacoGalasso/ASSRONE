package ASSRONE.backend.dto;

import ASSRONE.backend.model.EventType;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateEventRequest {

    @NotBlank(message = "Le titre est obligatoire")
    private String title;

    @NotBlank(message = "La description est obligatoire")
    @Size(max = 1000)
    private String description;

    @NotNull(message = "Le type d'événement est obligatoire")
    private EventType type;

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
