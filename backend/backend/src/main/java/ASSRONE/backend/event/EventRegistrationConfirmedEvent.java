package ASSRONE.backend.event;

import java.time.LocalDate;
import java.time.LocalTime;

public record EventRegistrationConfirmedEvent(
        Long eventId,
        String eventTitle,
        LocalDate eventDate,
        LocalTime startTime,
        LocalTime endTime,
        String location,
        Integer currentParticipants,
        Integer maxParticipants,
        String participantEmail,
        String participantFullName
) {
}
