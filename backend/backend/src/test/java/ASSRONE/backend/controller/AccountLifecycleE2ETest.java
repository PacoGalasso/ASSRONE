package ASSRONE.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Minimal end-to-end coverage of the full account lifecycle (Partie 15):
 * register -> verify email -> login -> forgot-password -> reset -> old
 * sessions/password dead, new password works. Drives the real HTTP endpoints
 * against a real Postgres (required — SessionService's login/refresh path
 * uses pg_advisory_xact_lock, unsupported by H2), exactly like
 * AvatarMissingFileSessionIntegrationTest.
 *
 * JavaMailSender is replaced by a Mockito mock: this is the "fake test email
 * service" required by Partie 6 — no real SMTP server is ever contacted, and
 * the raw verification/reset tokens (never exposed by any real API response)
 * are recovered here only by reading the link this test's own mock captured,
 * exactly as a real mailbox would show a human.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class AccountLifecycleE2ETest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final String EMAIL = "e2e-lifecycle@assrone.ch";
    private static final String INITIAL_PASSWORD = "MotDePasseInitial123";
    private static final String NEW_PASSWORD = "NouveauMotDePasse456";
    private static final Pattern TOKEN_IN_URL = Pattern.compile("token=([^\\s&]+)");

    @LocalServerPort
    private int port;

    @MockitoBean
    private JavaMailSender javaMailSender;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private static String extractRawTokenFromLatestMessage(JavaMailSender mailSender) {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, org.mockito.Mockito.atLeastOnce()).send(captor.capture());
        List<SimpleMailMessage> sent = captor.getAllValues();
        String body = sent.get(sent.size() - 1).getText();
        Matcher matcher = TOKEN_IN_URL.matcher(body);
        if (!matcher.find()) {
            throw new IllegalStateException("Aucun token trouvé dans le dernier email envoyé.");
        }
        return URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
    }

    @Test
    void cycleDeVieCompletDuCompte() throws Exception {
        HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

        // 1. Inscription — déclenche l'envoi (mocké) d'un email de vérification.
        HttpRequest registerRequest = HttpRequest.newBuilder(URI.create(baseUrl() + "/auth/addNewUser"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(java.util.Map.of(
                        "username", "e2e-lifecycle",
                        "email", EMAIL,
                        "firstName", "Test",
                        "lastName", "E2E",
                        "password", INITIAL_PASSWORD))))
                .build();
        HttpResponse<String> registerResponse = client.send(registerRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(registerResponse.statusCode()).isEqualTo(200);

        String verificationToken = extractRawTokenFromLatestMessage(javaMailSender);
        assertThat(verificationToken).isNotBlank();

        // 2. Connexion avant vérification — refusée (politique de connexion Option A).
        HttpRequest loginBeforeVerify = HttpRequest.newBuilder(URI.create(baseUrl() + "/auth/generateToken"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"" + EMAIL + "\",\"password\":\"" + INITIAL_PASSWORD + "\"}"))
                .build();
        HttpResponse<String> loginBeforeVerifyResponse = client.send(loginBeforeVerify, HttpResponse.BodyHandlers.ofString());
        assertThat(loginBeforeVerifyResponse.statusCode()).isEqualTo(403);

        // 3. Vérification de l'email via le token récupéré dans l'email (mocké).
        HttpRequest verifyRequest = HttpRequest.newBuilder(URI.create(baseUrl() + "/auth/verify-email"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"token\":\"" + verificationToken + "\"}"))
                .build();
        HttpResponse<String> verifyResponse = client.send(verifyRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(verifyResponse.statusCode()).isEqualTo(200);

        // 4. Connexion — réussit désormais, et établit une session/refresh token.
        HttpRequest loginRequest = HttpRequest.newBuilder(URI.create(baseUrl() + "/auth/generateToken"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"" + EMAIL + "\",\"password\":\"" + INITIAL_PASSWORD + "\"}"))
                .build();
        HttpResponse<String> loginResponse = client.send(loginRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(loginResponse.statusCode()).isEqualTo(200);

        // 5. Mot de passe oublié — déclenche l'envoi (mocké) d'un email de réinitialisation.
        HttpRequest forgotPasswordRequest = HttpRequest.newBuilder(URI.create(baseUrl() + "/auth/forgot-password"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + EMAIL + "\"}"))
                .build();
        HttpResponse<String> forgotPasswordResponse = client.send(forgotPasswordRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(forgotPasswordResponse.statusCode()).isEqualTo(200);

        String resetToken = extractRawTokenFromLatestMessage(javaMailSender);
        assertThat(resetToken).isNotBlank().isNotEqualTo(verificationToken);

        // 6. Réinitialisation avec le nouveau mot de passe.
        HttpRequest resetRequest = HttpRequest.newBuilder(URI.create(baseUrl() + "/auth/reset-password"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(java.util.Map.of(
                        "token", resetToken, "newPassword", NEW_PASSWORD))))
                .build();
        HttpResponse<String> resetResponse = client.send(resetRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(resetResponse.statusCode()).isEqualTo(200);

        // 7. L'ancienne session (refresh cookie obtenu à l'étape 4, toujours porté
        // par le CookieManager) est révoquée par le reset — /auth/refresh échoue.
        // Origin est requis ici : AuthCookieOriginFilter rejette /auth/refresh
        // en 403 quand Origin ET Referer sont absents (voir
        // AuthCookieOriginFilterIntegrationTest#refreshSansOrigineNiRefererRecoit403,
        // volontairement testé), ce que ce test doit franchir pour vérifier la
        // vraie révocation de session (401), pas cette protection distincte.
        HttpRequest refreshWithOldSession = HttpRequest.newBuilder(URI.create(baseUrl() + "/auth/refresh"))
                .header("Origin", "http://localhost:4200")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> refreshWithOldSessionResponse =
                client.send(refreshWithOldSession, HttpResponse.BodyHandlers.ofString());
        assertThat(refreshWithOldSessionResponse.statusCode()).isEqualTo(401);

        // 8. L'ancien mot de passe est refusé.
        HttpRequest loginWithOldPassword = HttpRequest.newBuilder(URI.create(baseUrl() + "/auth/generateToken"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"" + EMAIL + "\",\"password\":\"" + INITIAL_PASSWORD + "\"}"))
                .build();
        HttpResponse<String> loginWithOldPasswordResponse =
                client.send(loginWithOldPassword, HttpResponse.BodyHandlers.ofString());
        assertThat(loginWithOldPasswordResponse.statusCode()).isEqualTo(401);

        // 9. Le nouveau mot de passe fonctionne — cycle complet.
        HttpRequest loginWithNewPassword = HttpRequest.newBuilder(URI.create(baseUrl() + "/auth/generateToken"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"" + EMAIL + "\",\"password\":\"" + NEW_PASSWORD + "\"}"))
                .build();
        HttpResponse<String> loginWithNewPasswordResponse =
                client.send(loginWithNewPassword, HttpResponse.BodyHandlers.ofString());
        assertThat(loginWithNewPasswordResponse.statusCode()).isEqualTo(200);
        JsonNode finalBody = objectMapper.readTree(loginWithNewPasswordResponse.body());
        assertThat(finalBody.get("token").asText()).isNotBlank();
    }
}
