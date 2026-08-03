// service/EventService.java — remplace le fichier entier
package ASSRONE.backend.service;

import ASSRONE.backend.dto.CreateEventRequest;
import ASSRONE.backend.dto.EventDto;
import ASSRONE.backend.dto.EventRegistrationRequest;
import ASSRONE.backend.exception.EventFullException;
import ASSRONE.backend.exception.EventRegistrationAlreadyExistsException;
import ASSRONE.backend.exception.ResourceNotFoundException;
import ASSRONE.backend.mapper.EventMapper;
import ASSRONE.backend.model.Event;
import ASSRONE.backend.model.EventRegistration;
import ASSRONE.backend.repository.EventRegistrationRepository;
import ASSRONE.backend.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventRegistrationRepository eventRegistrationRepository;
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

    @Transactional
    public EventDto register(Long id, EventRegistrationRequest request) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Événement introuvable : " + id));

        if (event.getCurrentParticipants() >= event.getMaxParticipants()) {
            throw new EventFullException("Cet événement est complet.");
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);

        if (eventRegistrationRepository.existsByEventIdAndNormalizedEmail(id, normalizedEmail)) {
            throw new EventRegistrationAlreadyExistsException("Vous êtes déjà inscrit à cet événement.");
        }

        EventRegistration registration = EventRegistration.builder()
                .event(event)
                .fullName(request.getFullName())
                .email(request.getEmail())
                .normalizedEmail(normalizedEmail)
                .build();
        try {
            eventRegistrationRepository.saveAndFlush(registration);
        } catch (DataIntegrityViolationException ex) {
            throw new EventRegistrationAlreadyExistsException("Vous êtes déjà inscrit à cet événement.");
        }

        event.setCurrentParticipants(event.getCurrentParticipants() + 1);
        Event saved = eventRepository.save(event);

        eventEmailService.sendRegistrationConfirmation(saved, request);

        return eventMapper.toDto(saved);
    }

    @Transactional
    public void deleteEvent(Long id) {
        if (!eventRepository.existsById(id)) {
            throw new ResourceNotFoundException("Événement introuvable : " + id);
        }
        eventRegistrationRepository.deleteByEventId(id);
        eventRepository.deleteById(id);
    }
}