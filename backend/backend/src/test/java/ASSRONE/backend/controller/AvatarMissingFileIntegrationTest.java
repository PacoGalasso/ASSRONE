package ASSRONE.backend.controller;

import ASSRONE.backend.model.User;
import ASSRONE.backend.repository.UserInfoRepository;
import ASSRONE.backend.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduces and closes the exact bug reported after manual validation: a user
 * whose avatar_filename in the database still points at a file that no longer
 * exists on disk. Before UserProfileService#loadAvatar's existence check, this
 * surfaced as an uncaught FileNotFoundException from Resource#contentLength()
 * during response serialization — past the point any @ExceptionHandler can
 * intercept it — which this project's filter chain turns into a 401 with an
 * empty body (see ErrorResponseLeakageTest for the same underlying quirk with
 * a different trigger). The frontend's JwtInterceptor then treats that 401
 * exactly like an expired access token: it attempts a token refresh and, on
 * that refresh's own outcome, can log the user out — entirely because of a
 * missing image file, not any real authentication problem.
 *
 * A real embedded server (not MockMvc) is required for the same reason as
 * ErrorResponseLeakageTest: an exception thrown during message-converter body
 * writing needs a real servlet container to observe what a real client
 * actually receives. Access tokens are minted directly via JwtService rather
 * than through a real POST /auth/generateToken call: real login goes through
 * SessionService's session-limit enforcement, which takes a Postgres advisory
 * lock (pg_advisory_xact_lock) unsupported by the H2 database this fast,
 * Docker-free test class uses — see AvatarMissingFileSessionIntegrationTest
 * for the Postgres-backed coverage of the real login/refresh flow.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:avatar-missing-file-test;DB_CLOSE_DELAY=-1",
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
        "app.upload-dir=target/avatar-missing-file-test-uploads",
        "app.contact.recipient=test@example.invalid",
})
class AvatarMissingFileIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserInfoRepository userInfoRepository;

    @Autowired
    private JwtService jwtService;

    @Value("${app.upload-dir}")
    private String uploadDir;

    private final HttpClient client = HttpClient.newHttpClient();

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private String createUserAndGetToken(String email, String avatarFilename) {
        User user = User.builder()
                .email(email)
                .password("hash-non-utilise-dans-ce-test")
                .username(email.substring(0, email.indexOf('@')))
                .role("USER")
                .isActive(true)
                .avatarFilename(avatarFilename)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userInfoRepository.save(user);
        return jwtService.generateToken(email);
    }

    private HttpResponse<String> getAvatar(String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/profile/avatar"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void avatarExistantSurLeDisqueRetourne200() throws Exception {
        String token = createUserAndGetToken("avec-avatar@assrone.ch", "avatar-reel.jpg");
        Path avatarsDir = Path.of(uploadDir, "avatars");
        Files.createDirectories(avatarsDir);
        try (var stream = getClass().getResourceAsStream("/avatars/small-valid.jpg")) {
            Files.write(avatarsDir.resolve("avatar-reel.jpg"), stream.readAllBytes());
        }

        HttpResponse<String> response = getAvatar(token);

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void aucunAvatarEnBaseRetourne404VideSansDetailTechnique() throws Exception {
        String token = createUserAndGetToken("sans-avatar@assrone.ch", null);

        HttpResponse<String> response = getAvatar(token);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body() == null || response.body().isEmpty()).isTrue();
    }

    @Test
    void referenceEnBaseMaisFichierAbsentDuDisqueRetourne404ControleEtNonUne401Deguisee() throws Exception {
        String token = createUserAndGetToken("avatar-orphelin@assrone.ch", "31f169dd-2078-4a69-9f78-b2afb7d24732.jpg");

        HttpResponse<String> response = getAvatar(token);

        // The whole bug: before the fix this was 401 (an uncaught
        // FileNotFoundException disguised by this app's filter chain), which
        // the frontend interceptor would mistake for an authentication failure.
        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void aucunCheminAbsoluNiDetailInterneDansLeCorpsDeReponseQuandLeFichierEstAbsent() throws Exception {
        String token = createUserAndGetToken("avatar-orphelin-2@assrone.ch", "31f169dd-2078-4a69-9f78-b2afb7d24732.jpg");

        HttpResponse<String> response = getAvatar(token);

        String body = response.body() == null ? "" : response.body();
        assertThat(body).doesNotContain(uploadDir);
        assertThat(body).doesNotContain("FileNotFoundException");
        assertThat(body).doesNotContain("ASSRONE.backend");
        assertThat(body).doesNotContainIgnoringCase("cannot be resolved in the file system");
    }

    @Test
    void leMemeAccessTokenResteValideMalgreUnAvatarManquantSurLeDisque() throws Exception {
        String token = createUserAndGetToken("session-login@assrone.ch", "31f169dd-2078-4a69-9f78-b2afb7d24732.jpg");

        HttpResponse<String> avatarResponse = getAvatar(token);
        assertThat(avatarResponse.statusCode()).isEqualTo(404);

        // The same access token still works afterward — the avatar 404 never
        // invalidated it.
        HttpRequest profileRequest = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/profile"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> profileResponse = client.send(profileRequest, HttpResponse.BodyHandlers.ofString());

        assertThat(profileResponse.statusCode()).isEqualTo(200);
    }
}
