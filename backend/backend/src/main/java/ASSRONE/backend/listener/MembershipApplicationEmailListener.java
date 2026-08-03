package ASSRONE.backend.listener;

import ASSRONE.backend.event.MembershipApplicationSubmittedEvent;
import ASSRONE.backend.service.MembershipEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipApplicationEmailListener {

    private final MembershipEmailService membershipEmailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApplicationSubmitted(MembershipApplicationSubmittedEvent event) {
        try {
            membershipEmailService.sendApplicationSubmittedEmails(event);
        } catch (RuntimeException ex) {
            log.error("Échec de l'envoi des emails de demande d'adhésion pour la candidature {} (email : {})",
                    event.applicationId(), event.email(), ex);
        }
    }
}
