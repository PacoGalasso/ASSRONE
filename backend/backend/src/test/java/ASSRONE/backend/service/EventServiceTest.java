package ASSRONE.backend.service;

import ASSRONE.backend.dto.EventRegistrationRequest;
import ASSRONE.backend.exception.EventFullException;
import ASSRONE.backend.exception.EventRegistrationAlreadyExistsException;
import ASSRONE.backend.exception.ResourceNotFoundException;
import ASSRONE.backend.mapper.EventMapper;
import ASSRONE.backend.model.Event;
import ASSRONE.backend.model.EventRegistration;
import ASSRONE.backend.repository.EventRegistrationRepository;
import ASSRONE.backend.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventRegistrationRepository eventRegistrationRepository;

    @Mock
    private EventMapper eventMapper;

    @Mock
    private EventEmailService eventEmailService;

    private EventService service() {
        return new EventService(eventRepository, eventRegistrationRepository, eventMapper, eventEmailService);
    }

    private static Event evenementOuvert() {
        return Event.builder()
                .id(1L)
                .title("Atelier bénévolat")
                .maxParticipants(10)
                .currentParticipants(0)
                .build();
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

        verify(eventRegistrationRepository, never()).deleteByEventId(anyLong());
        verify(eventRepository, never()).deleteById(anyLong());
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

        verifyNoInteractions(eventRegistrationRepository);
        verify(eventRepository, never()).save(any());
        verifyNoInteractions(eventEmailService);
    }

    @Test
    void premiereInscriptionEstAccepteeEtIncrementeLeCompteur() {
        Event event = evenementOuvert();
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(eventRegistrationRepository.existsByEventIdAndNormalizedEmail(1L, "jean.dupont@assrone.ch")).thenReturn(false);
        when(eventRepository.save(any(Event.class))).thenReturn(event);
        when(eventMapper.toDto(event)).thenReturn(null);

        EventRegistrationRequest request = EventRegistrationRequest.builder()
                .fullName("Jean Dupont")
                .email("jean.dupont@assrone.ch")
                .build();

        service().register(1L, request);

        verify(eventRegistrationRepository).saveAndFlush(any(EventRegistration.class));
        assertThat(event.getCurrentParticipants()).isEqualTo(1);
        verify(eventEmailService).sendRegistrationConfirmation(event, request);
    }

    @Test
    void emailEstNormaliseEnMinusculesEtSansEspacesAvantPersistance() {
        Event event = evenementOuvert();
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(eventRegistrationRepository.existsByEventIdAndNormalizedEmail(1L, "jean.dupont@assrone.ch")).thenReturn(false);
        when(eventRepository.save(any(Event.class))).thenReturn(event);
        when(eventMapper.toDto(event)).thenReturn(null);

        EventRegistrationRequest request = EventRegistrationRequest.builder()
                .fullName("Jean Dupont")
                .email("  Jean.Dupont@ASSRONE.ch  ")
                .build();

        service().register(1L, request);

        ArgumentCaptor<EventRegistration> captor = ArgumentCaptor.forClass(EventRegistration.class);
        verify(eventRegistrationRepository).saveAndFlush(captor.capture());
        EventRegistration persisted = captor.getValue();
        assertThat(persisted.getNormalizedEmail()).isEqualTo("jean.dupont@assrone.ch");
        assertThat(persisted.getEmail()).isEqualTo("  Jean.Dupont@ASSRONE.ch  ");
    }

    @Test
    void memeEmailExactRejeteParVerificationApplicative() {
        Event event = evenementOuvert();
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(eventRegistrationRepository.existsByEventIdAndNormalizedEmail(1L, "jean.dupont@assrone.ch")).thenReturn(true);

        EventRegistrationRequest request = EventRegistrationRequest.builder()
                .fullName("Jean Dupont")
                .email("jean.dupont@assrone.ch")
                .build();

        assertThatThrownBy(() -> service().register(1L, request))
                .isInstanceOf(EventRegistrationAlreadyExistsException.class)
                .hasMessage("Vous êtes déjà inscrit à cet événement.");

        verify(eventRegistrationRepository, never()).saveAndFlush(any());
        verify(eventRepository, never()).save(any());
        verifyNoInteractions(eventEmailService);
    }

    @Test
    void variationDeCasseEstRejeteeCarNormaliseeAvantVerification() {
        Event event = evenementOuvert();
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(eventRegistrationRepository.existsByEventIdAndNormalizedEmail(1L, "jean.dupont@assrone.ch")).thenReturn(true);

        EventRegistrationRequest request = EventRegistrationRequest.builder()
                .fullName("Jean Dupont")
                .email("Jean.DUPONT@Assrone.CH")
                .build();

        assertThatThrownBy(() -> service().register(1L, request))
                .isInstanceOf(EventRegistrationAlreadyExistsException.class);

        verify(eventRegistrationRepository, never()).saveAndFlush(any());
    }

    @Test
    void variationDEspacesEstRejeteeCarNormaliseeAvantVerification() {
        Event event = evenementOuvert();
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(eventRegistrationRepository.existsByEventIdAndNormalizedEmail(1L, "jean.dupont@assrone.ch")).thenReturn(true);

        EventRegistrationRequest request = EventRegistrationRequest.builder()
                .fullName("Jean Dupont")
                .email("  jean.dupont@assrone.ch  ")
                .build();

        assertThatThrownBy(() -> service().register(1L, request))
                .isInstanceOf(EventRegistrationAlreadyExistsException.class);

        verify(eventRegistrationRepository, never()).saveAndFlush(any());
    }

    @Test
    void deuxEmailsDifferentsSontTousDeuxAcceptes() {
        Event event = evenementOuvert();
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(eventRegistrationRepository.existsByEventIdAndNormalizedEmail(eq(1L), anyString())).thenReturn(false);
        when(eventRepository.save(any(Event.class))).thenReturn(event);
        when(eventMapper.toDto(event)).thenReturn(null);

        EventService service = service();
        service.register(1L, EventRegistrationRequest.builder().fullName("Jean Dupont").email("jean.dupont@assrone.ch").build());
        service.register(1L, EventRegistrationRequest.builder().fullName("Marie Martin").email("marie.martin@assrone.ch").build());

        verify(eventRegistrationRepository, times(2)).saveAndFlush(any(EventRegistration.class));
        assertThat(event.getCurrentParticipants()).isEqualTo(2);
    }

    @Test
    void collisionDeContrainteUniqueEstTraduiteEnExceptionMetier() {
        Event event = evenementOuvert();
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(eventRegistrationRepository.existsByEventIdAndNormalizedEmail(1L, "jean.dupont@assrone.ch")).thenReturn(false);
        when(eventRegistrationRepository.saveAndFlush(any(EventRegistration.class)))
                .thenThrow(new DataIntegrityViolationException("contrainte uk_event_registration_event_email violée"));

        EventRegistrationRequest request = EventRegistrationRequest.builder()
                .fullName("Jean Dupont")
                .email("jean.dupont@assrone.ch")
                .build();

        assertThatThrownBy(() -> service().register(1L, request))
                .isInstanceOf(EventRegistrationAlreadyExistsException.class)
                .hasMessage("Vous êtes déjà inscrit à cet événement.");

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(eventEmailService);
    }

    @Test
    void suppressionDUnEvenementSupprimeDAbordSesInscriptions() {
        when(eventRepository.existsById(1L)).thenReturn(true);

        service().deleteEvent(1L);

        InOrder ordre = inOrder(eventRegistrationRepository, eventRepository);
        ordre.verify(eventRegistrationRepository).deleteByEventId(1L);
        ordre.verify(eventRepository).deleteById(1L);
    }

    @Test
    void suppressionDUnEvenementSansInscriptionSuitLeMemeOrdreNominal() {
        when(eventRepository.existsById(2L)).thenReturn(true);

        service().deleteEvent(2L);

        InOrder ordre = inOrder(eventRegistrationRepository, eventRepository);
        ordre.verify(eventRegistrationRepository).deleteByEventId(2L);
        ordre.verify(eventRepository).deleteById(2L);
    }
}
