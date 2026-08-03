package ASSRONE.backend.service;

import ASSRONE.backend.dto.EventDto;
import ASSRONE.backend.dto.EventRegistrationRequest;
import ASSRONE.backend.event.EventRegistrationConfirmedEvent;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalTime;
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
    private ApplicationEventPublisher eventPublisher;

    private EventService service() {
        return new EventService(eventRepository, eventRegistrationRepository, eventMapper, eventPublisher);
    }

    private static Event evenementOuvert(int currentParticipants) {
        return Event.builder()
                .id(1L)
                .title("Atelier bénévolat")
                .eventDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(20, 0))
                .location("Local associatif")
                .maxParticipants(10)
                .currentParticipants(currentParticipants)
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

        verify(eventRepository, never()).incrementParticipantsIfCapacityAvailable(anyLong());
        verifyNoInteractions(eventRegistrationRepository);
        verifyNoInteractions(eventPublisher);
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
    void updateAtomiqueSansLigneAffecteeLeveEventFullException() {
        Event event = evenementOuvert(10);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(eventRepository.incrementParticipantsIfCapacityAvailable(1L)).thenReturn(0);

        EventRegistrationRequest request = EventRegistrationRequest.builder()
                .fullName("Jean Dupont")
                .email("jean.dupont@assrone.ch")
                .build();

        assertThatThrownBy(() -> service().register(1L, request))
                .isInstanceOf(EventFullException.class)
                .hasMessage("Cet événement est complet.");

        verify(eventRepository, times(1)).findById(1L);
        verifyNoInteractions(eventRegistrationRepository);
        verify(eventRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void updateAtomiqueReussiRechargeLEvenementEtPublieLEvenementApplicatif() {
        Event avantIncrement = evenementOuvert(0);
        Event apresIncrement = evenementOuvert(1);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(avantIncrement), Optional.of(apresIncrement));
        when(eventRepository.incrementParticipantsIfCapacityAvailable(1L)).thenReturn(1);
        when(eventRegistrationRepository.existsByEventIdAndNormalizedEmail(1L, "jean.dupont@assrone.ch")).thenReturn(false);
        when(eventMapper.toDto(apresIncrement)).thenReturn(
                EventDto.builder().id(1L).currentParticipants(1).maxParticipants(10).build());

        EventRegistrationRequest request = EventRegistrationRequest.builder()
                .fullName("Jean Dupont")
                .email("jean.dupont@assrone.ch")
                .build();

        EventDto result = service().register(1L, request);

        verify(eventRepository, times(2)).findById(1L);
        verify(eventRepository, never()).save(any());
        assertThat(result.getCurrentParticipants()).isEqualTo(1);

        ArgumentCaptor<EventRegistration> registrationCaptor = ArgumentCaptor.forClass(EventRegistration.class);
        verify(eventRegistrationRepository).saveAndFlush(registrationCaptor.capture());
        assertThat(registrationCaptor.getValue().getEvent()).isSameAs(apresIncrement);

        ArgumentCaptor<EventRegistrationConfirmedEvent> eventCaptor = ArgumentCaptor.forClass(EventRegistrationConfirmedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        EventRegistrationConfirmedEvent published = eventCaptor.getValue();
        assertThat(published.eventId()).isEqualTo(1L);
        assertThat(published.participantEmail()).isEqualTo("jean.dupont@assrone.ch");
        assertThat(published.participantFullName()).isEqualTo("Jean Dupont");
        assertThat(published.currentParticipants()).isEqualTo(1);
        assertThat(published.maxParticipants()).isEqualTo(10);
    }

    @Test
    void emailEstNormaliseEnMinusculesEtSansEspacesAvantPersistance() {
        Event avantIncrement = evenementOuvert(0);
        Event apresIncrement = evenementOuvert(1);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(avantIncrement), Optional.of(apresIncrement));
        when(eventRepository.incrementParticipantsIfCapacityAvailable(1L)).thenReturn(1);
        when(eventRegistrationRepository.existsByEventIdAndNormalizedEmail(1L, "jean.dupont@assrone.ch")).thenReturn(false);
        when(eventMapper.toDto(apresIncrement)).thenReturn(EventDto.builder().id(1L).build());

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
    void memeEmailExactRejeteParVerificationApplicativeApresIncrement() {
        Event avantIncrement = evenementOuvert(0);
        Event apresIncrement = evenementOuvert(1);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(avantIncrement), Optional.of(apresIncrement));
        when(eventRepository.incrementParticipantsIfCapacityAvailable(1L)).thenReturn(1);
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
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void variationDeCasseEstRejeteeCarNormaliseeAvantVerification() {
        Event avantIncrement = evenementOuvert(0);
        Event apresIncrement = evenementOuvert(1);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(avantIncrement), Optional.of(apresIncrement));
        when(eventRepository.incrementParticipantsIfCapacityAvailable(1L)).thenReturn(1);
        when(eventRegistrationRepository.existsByEventIdAndNormalizedEmail(1L, "jean.dupont@assrone.ch")).thenReturn(true);

        EventRegistrationRequest request = EventRegistrationRequest.builder()
                .fullName("Jean Dupont")
                .email("Jean.DUPONT@Assrone.CH")
                .build();

        assertThatThrownBy(() -> service().register(1L, request))
                .isInstanceOf(EventRegistrationAlreadyExistsException.class);

        verify(eventRegistrationRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void variationDEspacesEstRejeteeCarNormaliseeAvantVerification() {
        Event avantIncrement = evenementOuvert(0);
        Event apresIncrement = evenementOuvert(1);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(avantIncrement), Optional.of(apresIncrement));
        when(eventRepository.incrementParticipantsIfCapacityAvailable(1L)).thenReturn(1);
        when(eventRegistrationRepository.existsByEventIdAndNormalizedEmail(1L, "jean.dupont@assrone.ch")).thenReturn(true);

        EventRegistrationRequest request = EventRegistrationRequest.builder()
                .fullName("Jean Dupont")
                .email("  jean.dupont@assrone.ch  ")
                .build();

        assertThatThrownBy(() -> service().register(1L, request))
                .isInstanceOf(EventRegistrationAlreadyExistsException.class);

        verify(eventRegistrationRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void deuxEmailsDifferentsSontTousDeuxAcceptesEtPublientChacunLeurEvenement() {
        Event event = evenementOuvert(0);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(eventRepository.incrementParticipantsIfCapacityAvailable(1L)).thenReturn(1);
        when(eventRegistrationRepository.existsByEventIdAndNormalizedEmail(eq(1L), anyString())).thenReturn(false);
        when(eventMapper.toDto(event)).thenReturn(EventDto.builder().id(1L).build());

        EventService service = service();
        service.register(1L, EventRegistrationRequest.builder().fullName("Jean Dupont").email("jean.dupont@assrone.ch").build());
        service.register(1L, EventRegistrationRequest.builder().fullName("Marie Martin").email("marie.martin@assrone.ch").build());

        verify(eventRegistrationRepository, times(2)).saveAndFlush(any(EventRegistration.class));
        verify(eventRepository, times(2)).incrementParticipantsIfCapacityAvailable(1L);
        verify(eventPublisher, times(2)).publishEvent(any(EventRegistrationConfirmedEvent.class));
    }

    @Test
    void collisionDeContrainteUniqueApresIncrementEstTraduiteEnExceptionMetierSansPublication() {
        Event avantIncrement = evenementOuvert(0);
        Event apresIncrement = evenementOuvert(1);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(avantIncrement), Optional.of(apresIncrement));
        when(eventRepository.incrementParticipantsIfCapacityAvailable(1L)).thenReturn(1);
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
        verifyNoInteractions(eventPublisher);
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
