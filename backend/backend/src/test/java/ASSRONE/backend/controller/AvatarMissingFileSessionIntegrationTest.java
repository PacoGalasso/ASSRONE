package ASSRONE.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Companion to AvatarMissingFileIntegrationTest: proves the real login and
 * refresh flows — which go through SessionService's session-limit
 * enforcement and its Postgres advisory lock (pg_advisory_xact_lock),
 * unsupported by H2 — are completely unaffected by a subsequent avatar
 * request failing because the referenced file is missing from disk. Requires
 * Docker; skipped automatically otherwise (disabledWithoutDocker = true).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class AvatarMissingFileSessionIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final String EMAIL = "avatar-session@assrone.ch";
    private static final String PASSWORD = "MotDePasseValide123";
    private static final String ORPHAN_AVATAR = "31f169dd-2078-4a69-9f78-b2afb7d24732.jpg";

    @LocalServerPort
    private int port;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    // Inserted via raw JDBC, deliberately bypassing the repository/entity
    // layer: this only needs a row present before the real HTTP login flow
    // takes over, and avoids pulling in the full user-registration path.
    private void seedUser() throws Exception {
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = conn.createStatement()) {
            statement.execute("""
                    INSERT INTO users (email, password, username, role, is_active, created_at, updated_at, avatar_filename)
                    VALUES ('%s', '%s', 'avatar-session', 'USER', true, now(), now(), '%s')
                    """.formatted(EMAIL, passwordEncoder.encode(PASSWORD), ORPHAN_AVATAR));
        }
    }

    @Test
    void loginPuisRefreshReussissentTousDeuxMalgreUnAvatarManquantEntreLesDeux() throws Exception {
        seedUser();
        HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

        HttpRequest loginRequest = HttpRequest.newBuilder(URI.create(baseUrl() + "/auth/generateToken"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .build();
        HttpResponse<String> loginResponse = client.send(loginRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(loginResponse.statusCode()).isEqualTo(200);
        String accessToken = objectMapper.readTree(loginResponse.body()).get("token").asText();

        HttpRequest avatarRequest = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/profile/avatar"))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        HttpResponse<String> avatarResponse = client.send(avatarRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(avatarResponse.statusCode()).isEqualTo(404);

        // The refresh cookie set at login (carried automatically by the
        // CookieManager) still rotates successfully — proving the avatar 404
        // in between never touched the session or the refresh token.
        HttpRequest refreshRequest = HttpRequest.newBuilder(URI.create(baseUrl() + "/auth/refresh"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> refreshResponse = client.send(refreshRequest, HttpResponse.BodyHandlers.ofString());

        assertThat(refreshResponse.statusCode()).isEqualTo(200);
        JsonNode refreshBody = objectMapper.readTree(refreshResponse.body());
        assertThat(refreshBody.get("token").asText()).isNotBlank();
    }
}
