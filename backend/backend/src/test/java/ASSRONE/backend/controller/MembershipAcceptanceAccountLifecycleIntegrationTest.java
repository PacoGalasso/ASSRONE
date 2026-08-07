package ASSRONE.backend.controller;

import ASSRONE.backend.dto.CreateMembershipApplicationRequest;
import ASSRONE.backend.dto.MembershipApplicationDto;
import ASSRONE.backend.event.MembershipApplicationAcceptedEvent;
import ASSRONE.backend.model.MembershipType;
import ASSRONE.backend.model.User;
import ASSRONE.backend.repository.EmailVerificationTokenRepository;
import ASSRONE.backend.repository.PasswordResetTokenRepository;
import ASSRONE.backend.repository.UserInfoRepository;
import ASSRONE.backend.service.MembershipApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * Proves the business rule confirmed for ASSRONE: the only normal
 * account-creation path is membership-application acceptance, and it must
 * never go through email verification — no token, no verification email, no
 * EMAIL_NOT_VERIFIED at login. Only the welcome email (credentials delivery)
 * is expected.
 *
 * <p>Requires real Postgres (Testcontainers, skipped without Docker): the
 * real POST /auth/generateToken call goes through SessionService's
 * session-limit enforcement, which takes a Postgres advisory lock
 * (pg_advisory_xact_lock) that H2 does not support — see
 * AvatarMissingFileIntegrationTest/AvatarMissingFileSessionIntegrationTest
 * for the same split. JavaMailSender is the only mock, so the actual email
 * content is asserted against, not just "some email was sent".
 */
@RecordApplicationEvents
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class MembershipAcceptanceAccountLifecycleIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private MembershipApplicationService membershipApplicationService;

    @Autowired
    private UserInfoRepository userInfoRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private ApplicationEvents events;

    @MockitoBean
    private JavaMailSender mailSender;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    void adhesionAccepteeCreeUnCompteImmediatementConnectableSansAucuneVerificationEmail() throws Exception {
        String email = "nouveau-membre@assrone.ch";

        MembershipApplicationDto submitted = membershipApplicationService.submit(
                CreateMembershipApplicationRequest.builder()
                        .fullName("Nouveau Membre")
                        .email(email)
                        .membershipType(MembershipType.INDIVIDUEL)
                        .charterAccepted(true)
                        .build());

        membershipApplicationService.accept(submitted.getId());

        // 2. compte créé avec email_verified_at non nul
        User user = userInfoRepository.findByEmail(email).orElseThrow();
        assertThat(user.getEmailVerifiedAt())
                .as("un compte issu de l'acceptation d'une adhésion est vérifié immédiatement")
                .isNotNull();

        // 3. aucun token de vérification créé
        assertThat(emailVerificationTokenRepository.count())
                .as("l'acceptation d'une adhésion ne doit jamais émettre de token de vérification email")
                .isZero();

        // 4. aucun email de vérification envoyé / 5. email de bienvenue envoyé
        ArgumentCaptor<SimpleMailMessage> mailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(mailCaptor.capture());
        List<SimpleMailMessage> sentMessages = mailCaptor.getAllValues();
        assertThat(sentMessages)
                .as("seul l'email de bienvenue (identifiants) doit partir, jamais un email de vérification")
                .anySatisfy(message -> assertThat(message.getSubject())
                        .isEqualTo("Bienvenue à ASSRONE — vos identifiants de connexion"))
                .noneSatisfy(message -> assertThat(message.getSubject())
                        .containsIgnoringCase("vérifi"));

        // 6. login immédiat réussi
        String rawPassword = events.stream(MembershipApplicationAcceptedEvent.class)
                .findFirst()
                .orElseThrow()
                .rawPassword();

        HttpRequest loginRequest = HttpRequest.newBuilder(URI.create(baseUrl() + "/auth/generateToken"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"" + email + "\",\"password\":\"" + rawPassword + "\"}"))
                .build();
        HttpResponse<String> loginResponse = client.send(loginRequest, HttpResponse.BodyHandlers.ofString());

        assertThat(loginResponse.statusCode())
                .as("un compte issu d'une adhésion acceptée ne doit jamais recevoir EMAIL_NOT_VERIFIED")
                .isEqualTo(200);
        assertThat(mapper.readTree(loginResponse.body()).get("token").asText()).isNotBlank();

        // 7. mot de passe oublié toujours fonctionnel
        HttpRequest forgotPasswordRequest = HttpRequest.newBuilder(URI.create(baseUrl() + "/auth/forgot-password"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + email + "\"}"))
                .build();
        HttpResponse<String> forgotPasswordResponse =
                client.send(forgotPasswordRequest, HttpResponse.BodyHandlers.ofString());

        assertThat(forgotPasswordResponse.statusCode()).isEqualTo(200);
        assertThat(passwordResetTokenRepository.count())
                .as("le mot de passe oublié reste fonctionnel pour un compte créé par adhésion")
                .isEqualTo(1);
    }
}
