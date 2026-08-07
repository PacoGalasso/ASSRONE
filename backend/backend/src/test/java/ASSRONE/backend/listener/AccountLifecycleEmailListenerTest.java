package ASSRONE.backend.listener;

import ASSRONE.backend.audit.SecurityAuditService;
import ASSRONE.backend.audit.SecurityEventResult;
import ASSRONE.backend.audit.SecurityEventType;
import ASSRONE.backend.event.EmailVerificationRequestedEvent;
import ASSRONE.backend.event.PasswordResetRequestedEvent;
import ASSRONE.backend.service.AccountLifecycleEmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class AccountLifecycleEmailListenerTest {

    // A direct (same-thread) executor for the tests below that only care
    // about delegation/exception-absorption logic, not genuine async timing
    // — keeps those tests fast and deterministic. The dedicated tests further
    // down use a real bounded ThreadPoolExecutor instead, specifically to
    // prove the async/rejection behavior itself.
    private static final java.util.concurrent.Executor DIRECT_EXECUTOR = Runnable::run;

    private ThreadPoolExecutor realBoundedExecutor;

    @AfterEach
    void shutdownRealExecutor() {
        if (realBoundedExecutor != null) {
            realBoundedExecutor.shutdownNow();
        }
    }

    @Test
    void onPasswordResetRequestedAppelleLeServiceExactementUneFoisAvecLeTokenEnClair() {
        AccountLifecycleEmailService emailService = mock(AccountLifecycleEmailService.class);
        SecurityAuditService securityAuditService = mock(SecurityAuditService.class);
        AccountLifecycleEmailListener listener =
                new AccountLifecycleEmailListener(emailService, securityAuditService, DIRECT_EXECUTOR);
        PasswordResetRequestedEvent event = new PasswordResetRequestedEvent("membre@assrone.ch", "token-en-clair");

        listener.onPasswordResetRequested(event);

        verify(emailService, times(1)).sendPasswordResetEmail("membre@assrone.ch", "token-en-clair");
        verify(securityAuditService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void onEmailVerificationRequestedAppelleLeServiceExactementUneFoisAvecLeTokenEnClair() {
        AccountLifecycleEmailService emailService = mock(AccountLifecycleEmailService.class);
        SecurityAuditService securityAuditService = mock(SecurityAuditService.class);
        AccountLifecycleEmailListener listener =
                new AccountLifecycleEmailListener(emailService, securityAuditService, DIRECT_EXECUTOR);
        EmailVerificationRequestedEvent event = new EmailVerificationRequestedEvent("membre@assrone.ch", "token-en-clair");

        listener.onEmailVerificationRequested(event);

        verify(emailService, times(1)).sendVerificationEmail("membre@assrone.ch", "token-en-clair");
        verify(securityAuditService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void exceptionSmtpSurResetEstAbsorbeeEtNeSortJamaisDuListener() {
        AccountLifecycleEmailService emailService = mock(AccountLifecycleEmailService.class);
        SecurityAuditService securityAuditService = mock(SecurityAuditService.class);
        doThrow(new RuntimeException("Échec SMTP simulé"))
                .when(emailService).sendPasswordResetEmail(any(), any());
        AccountLifecycleEmailListener listener =
                new AccountLifecycleEmailListener(emailService, securityAuditService, DIRECT_EXECUTOR);

        assertThatCode(() -> listener.onPasswordResetRequested(
                new PasswordResetRequestedEvent("membre@assrone.ch", "un-token")))
                .doesNotThrowAnyException();
    }

    @Test
    void exceptionSmtpSurResetJournaliseUnEchecSansTokenNiEmail() {
        AccountLifecycleEmailService emailService = mock(AccountLifecycleEmailService.class);
        SecurityAuditService securityAuditService = mock(SecurityAuditService.class);
        doThrow(new RuntimeException("Échec SMTP simulé"))
                .when(emailService).sendPasswordResetEmail(any(), any());
        AccountLifecycleEmailListener listener =
                new AccountLifecycleEmailListener(emailService, securityAuditService, DIRECT_EXECUTOR);

        listener.onPasswordResetRequested(new PasswordResetRequestedEvent("membre@assrone.ch", "un-token-secret"));

        verify(securityAuditService).record(SecurityEventType.ACCOUNT_LIFECYCLE_EMAIL_SEND_FAILED,
                SecurityEventResult.ERROR, "-", null, "accountLifecycleEmail", null, "PASSWORD_RESET");
    }

    @Test
    void exceptionSmtpSurVerificationEstAbsorbeeEtNeSortJamaisDuListener() {
        AccountLifecycleEmailService emailService = mock(AccountLifecycleEmailService.class);
        SecurityAuditService securityAuditService = mock(SecurityAuditService.class);
        doThrow(new RuntimeException("Échec SMTP simulé"))
                .when(emailService).sendVerificationEmail(any(), any());
        AccountLifecycleEmailListener listener =
                new AccountLifecycleEmailListener(emailService, securityAuditService, DIRECT_EXECUTOR);

        assertThatCode(() -> listener.onEmailVerificationRequested(
                new EmailVerificationRequestedEvent("membre@assrone.ch", "un-token")))
                .doesNotThrowAnyException();
    }

    @Test
    void exceptionSmtpSurVerificationJournaliseUnEchecSansTokenNiEmail() {
        AccountLifecycleEmailService emailService = mock(AccountLifecycleEmailService.class);
        SecurityAuditService securityAuditService = mock(SecurityAuditService.class);
        doThrow(new RuntimeException("Échec SMTP simulé"))
                .when(emailService).sendVerificationEmail(any(), any());
        AccountLifecycleEmailListener listener =
                new AccountLifecycleEmailListener(emailService, securityAuditService, DIRECT_EXECUTOR);

        listener.onEmailVerificationRequested(new EmailVerificationRequestedEvent("membre@assrone.ch", "un-token-secret"));

        verify(securityAuditService).record(SecurityEventType.ACCOUNT_LIFECYCLE_EMAIL_SEND_FAILED,
                SecurityEventResult.ERROR, "-", null, "accountLifecycleEmail", null, "EMAIL_VERIFICATION");
    }

    // ===== Comportement réellement asynchrone (exécuteur borné réel) =====

    @Test
    void onPasswordResetRequestedRevientImmediatementMemeSiLEnvoiEstArtificiellementLent() throws Exception {
        AccountLifecycleEmailService emailService = mock(AccountLifecycleEmailService.class);
        SecurityAuditService securityAuditService = mock(SecurityAuditService.class);
        CountDownLatch sendStarted = new CountDownLatch(1);
        CountDownLatch releaseSend = new CountDownLatch(1);
        doAnswer(invocation -> {
            sendStarted.countDown();
            // Simule un serveur SMTP lent (Mailpit indisponible, latence réseau, etc.) :
            // le thread du pool reste bloqué ici jusqu'à libération explicite par le test.
            assertThat(releaseSend.await(5, TimeUnit.SECONDS)).as("le test a bien libéré l'envoi").isTrue();
            return null;
        }).when(emailService).sendPasswordResetEmail(any(), any());
        realBoundedExecutor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1));
        AccountLifecycleEmailListener listener =
                new AccountLifecycleEmailListener(emailService, securityAuditService, realBoundedExecutor);

        long start = System.nanoTime();
        listener.onPasswordResetRequested(new PasswordResetRequestedEvent("membre@assrone.ch", "un-token"));
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        // #then: le listener (donc, en production, le retour du callback
        // AFTER_COMMIT qui bloquerait sinon la réponse HTTP) revient en
        // quelques millisecondes, bien avant que l'envoi bloqué (5s) ne
        // se termine — la seule preuve qui compte réellement ici est
        // qu'il ne dépend plus de la latence SMTP.
        assertThat(elapsedMillis).as("le listener ne doit jamais attendre l'envoi SMTP").isLessThan(1000);
        assertThat(sendStarted.await(2, TimeUnit.SECONDS))
                .as("l'envoi doit néanmoins démarrer, de façon asynchrone")
                .isTrue();
        releaseSend.countDown();

        // 2. l'email est quand même envoyé ensuite
        verify(emailService, timeout(2000)).sendPasswordResetEmail("membre@assrone.ch", "un-token");
    }

    @Test
    void uneErreurSmtpAsynchroneEstAuditeeSansJamaisRemonterAuThreadAppelant() {
        AccountLifecycleEmailService emailService = mock(AccountLifecycleEmailService.class);
        SecurityAuditService securityAuditService = mock(SecurityAuditService.class);
        doThrow(new RuntimeException("Échec SMTP simulé"))
                .when(emailService).sendPasswordResetEmail(any(), any());
        realBoundedExecutor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1));
        AccountLifecycleEmailListener listener =
                new AccountLifecycleEmailListener(emailService, securityAuditService, realBoundedExecutor);

        // 3. une erreur SMTP (ici, asynchrone et réelle) n'altère jamais
        // l'appel du listener lui-même — c'est ce qui garantit que la
        // réponse générique déjà décidée par le contrôleur ne peut jamais
        // en dépendre.
        assertThatCode(() -> listener.onPasswordResetRequested(
                new PasswordResetRequestedEvent("membre@assrone.ch", "un-token")))
                .doesNotThrowAnyException();

        verify(securityAuditService, timeout(2000)).record(SecurityEventType.ACCOUNT_LIFECYCLE_EMAIL_SEND_FAILED,
                SecurityEventResult.ERROR, "-", null, "accountLifecycleEmail", null, "PASSWORD_RESET");
    }

    @Test
    void unExecuteurSatureRejetteProprementEtAuditeSansException() throws Exception {
        AccountLifecycleEmailService emailService = mock(AccountLifecycleEmailService.class);
        SecurityAuditService securityAuditService = mock(SecurityAuditService.class);
        CountDownLatch blockFirstTask = new CountDownLatch(1);
        doAnswer(invocation -> {
            assertThat(blockFirstTask.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(emailService).sendPasswordResetEmail(any(), any());
        // Un seul thread, aucune file d'attente (SynchronousQueue n'a aucune
        // capacité de stockage propre — ArrayBlockingQueue rejette une
        // capacité de 0) : la deuxième soumission est rejetée pendant que la
        // première occupe l'unique thread disponible.
        realBoundedExecutor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new SynchronousQueue<>());
        AccountLifecycleEmailListener listener =
                new AccountLifecycleEmailListener(emailService, securityAuditService, realBoundedExecutor);
        listener.onPasswordResetRequested(new PasswordResetRequestedEvent("premier@assrone.ch", "token-1"));
        // Laisse le pool réellement occuper son unique thread avant la
        // seconde soumission, pour garantir un rejet déterministe.
        Thread.sleep(100);

        assertThatCode(() -> listener.onPasswordResetRequested(
                new PasswordResetRequestedEvent("second@assrone.ch", "token-2")))
                .as("une soumission rejetée ne doit jamais remonter au code appelant")
                .doesNotThrowAnyException();

        verify(securityAuditService).record(SecurityEventType.ACCOUNT_LIFECYCLE_EMAIL_SEND_FAILED,
                SecurityEventResult.ERROR, "-", null, "accountLifecycleEmail", null, "PASSWORD_RESET_QUEUE_SATURATED");
        blockFirstTask.countDown();
    }
}
