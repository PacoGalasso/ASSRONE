package ASSRONE.backend.service;

import ASSRONE.backend.dto.EventRegistrationRequest;
import ASSRONE.backend.exception.EventFullException;
import ASSRONE.backend.exception.ResourceNotFoundException;
import ASSRONE.backend.mapper.EventMapper;
import ASSRONE.backend.model.Event;
import ASSRONE.backend.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventMapper eventMapper;

    @Mock
    private EventEmailService eventEmailService;

    private EventService service() {
        return new EventService(eventRepository, eventMapper, eventEmailService);
    }

    @Test
    void registerAvecUnIdInexistantLeveResourceNotFound() {
        when(eventRepository.findById(42L)).thenReturn(Optional.empty());
        EventRegistrationRequest request = EventRegistrationRequest.builder()
                .fullName("Jean Dupont")
                .email("jean.dupont@assrone.ch")
                .build();

        assertThatThrownBy(() -> service().register(42L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Événement introuvable : 42");
    }

    @Test
    void deleteEventAvecUnIdInexistantLeveResourceNotFound() {
        when(eventRepository.existsById(42L)).thenReturn(false);

        assertThatThrownBy(() -> service().deleteEvent(42L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Événement introuvable : 42");
    }

    @Test
    void registerSurUnEvenementCompletLeveToujoursEventFullException() {
        Event evenementComplet = Event.builder()
                .id(1L)
                .title("Atelier bénévolat")
                .maxParticipants(10)
                .currentParticipants(10)
                .build();
        when(eventRepository.findById(1L)).thenReturn(Optional.of(evenementComplet));
        EventRegistrationRequest request = EventRegistrationRequest.builder()
                .fullName("Jean Dupont")
                .email("jean.dupont@assrone.ch")
                .build();

        assertThatThrownBy(() -> service().register(1L, request))
                .isInstanceOf(EventFullException.class);
    }
}
