// service/EventService.java — remplace le fichier entier
package ASSRONE.backend.service;

import ASSRONE.backend.dto.CreateEventRequest;
import ASSRONE.backend.dto.EventDto;
import ASSRONE.backend.dto.EventRegistrationRequest;
import ASSRONE.backend.exception.EventFullException;
import ASSRONE.backend.exception.ResourceNotFoundException;
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
    private final EventEmailService eventEmailService;

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

    public EventDto register(Long id, EventRegistrationRequest request) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Événement introuvable : " + id));

        if (event.getCurrentParticipants() >= event.getMaxParticipants()) {
            throw new EventFullException("Cet événement est complet.");
        }

        event.setCurrentParticipants(event.getCurrentParticipants() + 1);
        Event saved = eventRepository.save(event);

        eventEmailService.sendRegistrationConfirmation(saved, request);

        return eventMapper.toDto(saved);
    }

    public void deleteEvent(Long id) {
        if (!eventRepository.existsById(id)) {
            throw new ResourceNotFoundException("Événement introuvable : " + id);
        }
        eventRepository.deleteById(id);
    }
}