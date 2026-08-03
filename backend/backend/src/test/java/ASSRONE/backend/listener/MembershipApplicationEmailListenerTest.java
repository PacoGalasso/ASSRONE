package ASSRONE.backend.listener;

import ASSRONE.backend.event.MembershipApplicationSubmittedEvent;
import ASSRONE.backend.model.MembershipType;
import ASSRONE.backend.service.MembershipEmailService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class MembershipApplicationEmailListenerTest {

    private static MembershipApplicationSubmittedEvent evenement() {
        return new MembershipApplicationSubmittedEvent(
                1L,
                "Jean Dupont",
                "jean.dupont@assrone.ch",
                "0791234567",
                MembershipType.INDIVIDUEL,
                "Bonjour, je souhaite adhérer."
        );
    }

    @Test
    void appelleLenvoiDesDeuxEmailsExactementUneFois() {
        MembershipEmailService membershipEmailService = mock(MembershipEmailService.class);
        MembershipApplicationEmailListener listener = new MembershipApplicationEmailListener(membershipEmailService);
        MembershipApplicationSubmittedEvent event = evenement();

        listener.onApplicationSubmitted(event);

        verify(membershipEmailService, times(1)).sendApplicationSubmittedEmails(event);
    }

    @Test
    void exceptionSmtpEstAbsorbeeEtNeSortJamaisDuListener() {
        MembershipEmailService membershipEmailService = mock(MembershipEmailService.class);
        doThrow(new RuntimeException("Échec SMTP simulé"))
                .when(membershipEmailService).sendApplicationSubmittedEmails(any());
        MembershipApplicationEmailListener listener = new MembershipApplicationEmailListener(membershipEmailService);

        assertThatCode(() -> listener.onApplicationSubmitted(evenement())).doesNotThrowAnyException();
    }
}
