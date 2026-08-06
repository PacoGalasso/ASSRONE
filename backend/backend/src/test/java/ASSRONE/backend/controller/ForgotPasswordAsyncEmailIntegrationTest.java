package ASSRONE.backend.controller;

import ASSRONE.backend.model.User;
import ASSRONE.backend.repository.PasswordResetTokenRepository;
import ASSRONE.backend.repository.UserInfoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Proves the real bug reported after manual Mailpit validation: "Mot de
 * passe oublié" stayed on "Envoi en cours..." because
 * AccountLifecycleEmailListener ran the SMTP send synchronously, on the same
 * request thread, inside the AFTER_COMMIT callback — meaning the HTTP
 * response was only written after that callback returned. These tests
 * artificially slow down / fail JavaMailSender.send() to make that latency
 * observable, and assert the request no longer depends on it. Real Postgres
 * is not needed here (no Flyway-specific behavior under test), so this runs
 * without Docker, unlike the Testcontainers-backed tests elsewhere in this
 * module.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:forgot-password-async-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.flyway.locations=classpath:db/no-migrations-for-context-loads-test",
        "spring.flyway.fail-on-missing-locations=false",
        "app.jwt.secret=zTjvaDrwlDQTMdHQ9vSfqXGwdkGSXJtT09uCOP+KLfO0RmyO617AZi/hK7VKCiKe",
        "spring.mail.host=localhost",
        "spring.mail.port=2525",
        "spring.mail.username=test@example.invalid",
        "spring.mail.password=test-password-not-real",
        "app.upload-dir=target/forgot-password-async-test-uploads",
        "app.contact.recipient=test@example.invalid",
})
class ForgotPasswordAsyncEmailIntegrationTest {

    // Comfortably above realistic response time (a handful of milliseconds)
    // but far below the artificial SMTP delay used below, so this cannot
    // pass by accident on a slow CI runner.
    private static final long MAX_ACCEPTABLE_RESPONSE_MILLIS = 1500;
    private static final long ARTIFICIAL_SMTP_DELAY_MILLIS = 3000;

    @LocalServerPort
    private int port;

    @Autowired
    private UserInfoRepository userInfoRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private JavaMailSender mailSender;

    private final HttpClient client = HttpClient.newHttpClient();

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    // Each test seeds its own, distinctly-named account: this module's H2
    // database is shared across every test method in this class
    // (DB_CLOSE_DELAY=-1, same context), so a shared email would collide on
    // the users.email unique constraint on the second test to run.
    private Long seedUser(String email) {
        User saved = userInfoRepository.save(User.builder()
                .email(email)
                .username(email.substring(0, email.indexOf('@')))
                .password(passwordEncoder.encode("MotDePasseInitial123"))
                .role("USER")
                .emailVerifiedAt(LocalDateTime.now())
                .build());
        return saved.getId();
    }

    private HttpResponse<String> forgotPassword(String email) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/auth/forgot-password"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + email + "\"}"))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void repondRapidementMemeAvecUnEnvoiSmtpArtificiellementLent() throws Exception {
        String email = "membre-reset-lent@assrone.ch";
        Long userId = seedUser(email);
        CountDownLatch sendStarted = new CountDownLatch(1);
        doAnswer(invocation -> {
            sendStarted.countDown();
            Thread.sleep(ARTIFICIAL_SMTP_DELAY_MILLIS);
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));

        // 1. la réponse HTTP générique doit revenir rapidement
        long start = System.nanoTime();
        HttpResponse<String> response = forgotPassword(email);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(elapsedMillis)
                .as("la réponse ne doit plus jamais dépendre de la latence SMTP (délai artificiel : %d ms)",
                        ARTIFICIAL_SMTP_DELAY_MILLIS)
                .isLessThan(MAX_ACCEPTABLE_RESPONSE_MILLIS);

        // 4. le token reste bien persisté, immédiatement, avant même que l'envoi ne démarre
        assertThat(passwordResetTokenRepository.countByUserIdAndUsedAtIsNull(userId))
                .as("le token doit être en base dès la réponse HTTP, indépendamment de l'envoi")
                .isEqualTo(1);

        // 2. l'email est quand même envoyé ensuite (asynchrone)
        assertThat(sendStarted.await(2, TimeUnit.SECONDS)).as("l'envoi démarre bien, de façon asynchrone").isTrue();
        verify(mailSender, timeout(ARTIFICIAL_SMTP_DELAY_MILLIS + 2000)).send(any(SimpleMailMessage.class));
    }

    @Test
    void uneErreurSmtpNAltereJamaisLaReponseGeneriqueDejaEnvoyee() throws Exception {
        String email = "membre-reset-echec-smtp@assrone.ch";
        seedUser(email);
        doThrow(new org.springframework.mail.MailSendException("Connexion SMTP refusée (simulée)"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        // 3. une erreur SMTP n'altère pas la réponse générique
        HttpResponse<String> response = forgotPassword(email);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("Si un compte correspond à cette adresse, un email de réinitialisation a été envoyé.");
    }

    @Test
    void deuxDemandesSuccessivesEnvoientChacuneLeurPropreEmailApresCommitEtInvalidentLancien() throws Exception {
        String email = "membre-reset-deux-demandes@assrone.ch";
        Long userId = seedUser(email);
        AtomicInteger sendCount = new AtomicInteger();
        doAnswer(invocation -> {
            sendCount.incrementAndGet();
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));

        forgotPassword(email);
        forgotPassword(email);

        // 5. deux demandes successives : l'ancien token est invalidé (voir
        // aussi PasswordResetServiceTest pour la preuve unitaire de
        // invalidateAllActiveForUser), et chaque demande déclenche bien son
        // propre envoi asynchrone après commit.
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(2000).times(2)).send(captor.capture());
        assertThat(passwordResetTokenRepository.countByUserIdAndUsedAtIsNull(userId))
                .as("un seul token actif à la fois pour ce compte — le précédent est invalidé, pas dupliqué")
                .isEqualTo(1);
    }

    @Test
    void aucunEmailNiTokenPourUnCompteInexistantAntiEnumeration() throws Exception {
        long tokensBefore = passwordResetTokenRepository.count();

        HttpResponse<String> response = forgotPassword("inconnu-async@assrone.ch");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("Si un compte correspond à cette adresse, un email de réinitialisation a été envoyé.");
        assertThat(passwordResetTokenRepository.count())
                .as("aucun token créé pour une adresse qui ne correspond à aucun compte")
                .isEqualTo(tokensBefore);
        verify(mailSender, timeout(500).times(0)).send(any(SimpleMailMessage.class));
    }
}
