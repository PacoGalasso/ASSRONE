package ASSRONE.backend.listener;

import ASSRONE.backend.event.MembershipApplicationAcceptedEvent;
import ASSRONE.backend.service.MembershipEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipApplicationAcceptedEmailListener {

    private final MembershipEmailService membershipEmailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApplicationAccepted(MembershipApplicationAcceptedEvent event) {
        try {
            membershipEmailService.sendAccountCreated(event);
        } catch (RuntimeException ex) {
            log.error("Échec de l'envoi de l'email de bienvenue pour la candidature {} (email : {})",
                    event.applicationId(), event.email(), ex);
        }
    }
}
