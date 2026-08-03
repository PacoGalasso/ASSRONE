package ASSRONE.backend.listener;

import ASSRONE.backend.event.EventRegistrationConfirmedEvent;
import ASSRONE.backend.service.EventEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventRegistrationEmailListener {

    private final EventEmailService eventEmailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRegistrationConfirmed(EventRegistrationConfirmedEvent event) {
        try {
            eventEmailService.sendRegistrationConfirmation(event);
        } catch (RuntimeException ex) {
            log.error("Échec de l'envoi de l'email de confirmation d'inscription pour l'événement {} (participant : {})",
                    event.eventId(), event.participantEmail(), ex);
        }
    }
}
