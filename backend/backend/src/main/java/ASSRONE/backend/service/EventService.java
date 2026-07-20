// service/EventService.java — remplace le fichier entier
package ASSRONE.backend.service;

import ASSRONE.backend.dto.CreateEventRequest;
import ASSRONE.backend.dto.EventDto;
import ASSRONE.backend.mapper.EventMapper;
import ASSRONE.backend.model.Event;
import ASSRONE.backend.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;

    public List<EventDto> getUpcomingEvents() {
        return eventRepository.findByEventDateGreaterThanEqualOrderByEventDateAsc(LocalDate.now())
                .stream()
                .map(eventMapper::toDto)
                .toList();
    }

    public List<EventDto> getAllEvents() {
        return eventRepository.findAllByOrderByEventDateAsc()
                .stream()
                .map(eventMapper::toDto)
                .toList();
    }

    public EventDto createEvent(CreateEventRequest request) {
        Event event = eventMapper.fromCreateRequest(request);
        Event saved = eventRepository.save(event);
        return eventMapper.toDto(saved);
    }
}