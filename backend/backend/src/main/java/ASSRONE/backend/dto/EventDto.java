package ASSRONE.backend.dto;

import ASSRONE.backend.model.EventType;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventDto {
    private Long id;
    private String title;
    private String description;
    private EventType type;
    private LocalDate eventDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String location;
    private Integer maxParticipants;
    private Integer currentParticipants;
}
