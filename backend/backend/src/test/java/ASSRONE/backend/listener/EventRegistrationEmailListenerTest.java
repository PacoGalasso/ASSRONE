package ASSRONE.backend.listener;

import ASSRONE.backend.event.EventRegistrationConfirmedEvent;
import ASSRONE.backend.service.EventEmailService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class EventRegistrationEmailListenerTest {

    private static EventRegistrationConfirmedEvent evenement() {
        return new EventRegistrationConfirmedEvent(
                1L,
                "Atelier bénévolat",
                LocalDate.now().plusDays(1),
                LocalTime.of(18, 0),
                LocalTime.of(20, 0),
                "Local associatif",
                1,
                10,
                "jean.dupont@assrone.ch",
                "Jean Dupont"
        );
    }

    @Test
    void appelleLeServiceEmailExactementUneFois() {
        EventEmailService eventEmailService = mock(EventEmailService.class);
        EventRegistrationEmailListener listener = new EventRegistrationEmailListener(eventEmailService);
        EventRegistrationConfirmedEvent event = evenement();

        listener.onRegistrationConfirmed(event);

        verify(eventEmailService, times(1)).sendRegistrationConfirmation(event);
    }

    @Test
    void exceptionSmtpEstAbsorbeeEtNeSortJamaisDuListener() {
        EventEmailService eventEmailService = mock(EventEmailService.class);
        doThrow(new RuntimeException("Échec SMTP simulé"))
                .when(eventEmailService).sendRegistrationConfirmation(org.mockito.ArgumentMatchers.any());
        EventRegistrationEmailListener listener = new EventRegistrationEmailListener(eventEmailService);

        assertThatCode(() -> listener.onRegistrationConfirmed(evenement())).doesNotThrowAnyException();
    }
}
