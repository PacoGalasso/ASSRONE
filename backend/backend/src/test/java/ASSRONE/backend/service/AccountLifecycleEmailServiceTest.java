package ASSRONE.backend.service;

import ASSRONE.backend.config.AccountLifecycleProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies the actual email content this application sends — subject, body,
 * link construction, token URL-encoding — against a mocked JavaMailSender:
 * a real local SMTP server was deliberately not stood up for this test (see
 * docker-compose.yml's Mailpit service for that, used for manual local
 * validation instead), since Spring's JavaMailSender contract is exactly
 * what AccountLifecycleEmailService is coded against and a fake
 * implementation of that contract is a faithful, fast, hermetic substitute
 * for it in an automated test — no real network socket is more correct
 * here, only slower and flakier.
 */
class AccountLifecycleEmailServiceTest {

    private JavaMailSender mailSender;
    private AccountLifecycleEmailService service;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        AccountLifecycleProperties properties = new AccountLifecycleProperties(
                "https://assrone.ch",
                new AccountLifecycleProperties.ResetToken(Duration.ofHours(1)),
                new AccountLifecycleProperties.VerificationToken(Duration.ofHours(24)),
                new AccountLifecycleProperties.Cleanup(true, "0 30 3 * * *", Duration.ofDays(7)));
        service = new AccountLifecycleEmailService(mailSender, properties);
        ReflectionTestUtils.setField(service, "fromAddress", "noreply@assrone.ch");
    }

    // Returns the most recently sent message — some tests in this class send
    // more than once (e.g. to compare reset vs verification subjects), so
    // this deliberately never asserts an exact invocation count.
    private SimpleMailMessage sentMessage() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void sendPasswordResetEmailEnvoieDepuisLAdresseConfiguree() {
        service.sendPasswordResetEmail("membre@assrone.ch", "un-token-secret");

        SimpleMailMessage message = sentMessage();
        assertThat(message.getFrom()).isEqualTo("noreply@assrone.ch");
        assertThat(message.getTo()).containsExactly("membre@assrone.ch");
    }

    @Test
    void sendPasswordResetEmailContientLeLienDeReinitialisationAvecLeTokenEncode() {
        service.sendPasswordResetEmail("membre@assrone.ch", "token avec espace");

        String body = sentMessage().getText();
        assertThat(body).contains("https://assrone.ch/reset-password?token=token+avec+espace");
    }

    @Test
    void sendPasswordResetEmailIndiqueLaDureeDeValidite() {
        service.sendPasswordResetEmail("membre@assrone.ch", "un-token");

        assertThat(sentMessage().getText()).contains("1 heure");
    }

    @Test
    void sendPasswordResetEmailNeContientJamaisLeTokenEnClairHorsDuLien() {
        service.sendPasswordResetEmail("membre@assrone.ch", "un-token-tres-secret");

        SimpleMailMessage message = sentMessage();
        assertThat(message.getSubject()).doesNotContain("un-token-tres-secret");
        // The token appears exactly once — inside the reset link itself, never repeated
        // elsewhere in the body (e.g. accidentally logged into a footer or debug line).
        String body = message.getText();
        assertThat(body.split("un-token-tres-secret", -1)).hasSize(2);
    }

    @Test
    void sendVerificationEmailContientLeLienDeVerificationAvecLeTokenEncode() {
        service.sendVerificationEmail("membre@assrone.ch", "token avec espace");

        String body = sentMessage().getText();
        assertThat(body).contains("https://assrone.ch/verify-email?token=token+avec+espace");
    }

    @Test
    void sendVerificationEmailIndiqueLaDureeDeValiditeEnHeures() {
        service.sendVerificationEmail("membre@assrone.ch", "un-token");

        assertThat(sentMessage().getText()).contains("24 heures");
    }

    @Test
    void sendVerificationEmailEtSendPasswordResetEmailUtilisentDesSujetsDistincts() {
        service.sendPasswordResetEmail("membre@assrone.ch", "token-a");
        String resetSubject = sentMessage().getSubject();

        service.sendVerificationEmail("membre@assrone.ch", "token-b");
        String verificationSubject = sentMessage().getSubject();

        assertThat(resetSubject).isNotEqualTo(verificationSubject);
    }

    @Test
    void construitLeLienDepuisLaProprieteFrontendUrlJamaisUneValeurCodeEnDur() {
        AccountLifecycleProperties customProperties = new AccountLifecycleProperties(
                "https://staging.assrone.ch",
                new AccountLifecycleProperties.ResetToken(Duration.ofHours(1)),
                new AccountLifecycleProperties.VerificationToken(Duration.ofHours(24)),
                new AccountLifecycleProperties.Cleanup(true, "0 30 3 * * *", Duration.ofDays(7)));
        AccountLifecycleEmailService customService = new AccountLifecycleEmailService(mailSender, customProperties);
        ReflectionTestUtils.setField(customService, "fromAddress", "noreply@assrone.ch");

        customService.sendPasswordResetEmail("membre@assrone.ch", "un-token");

        assertThat(sentMessage().getText()).contains("https://staging.assrone.ch/reset-password?token=un-token");
    }
}
