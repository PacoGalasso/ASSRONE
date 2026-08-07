package ASSRONE.backend.controller;

import ASSRONE.backend.BackendApplication;
import ASSRONE.backend.config.LegacyBaselineFlywayCallback;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that an admin account and a regular member account that existed
 * BEFORE this lot (i.e. at the real V8 schema) can still log in after the
 * database is upgraded to V11, using a real HTTP call against a real,
 * fully-booted application.
 *
 * <p><b>Why phase 1 never boots Spring Boot</b>: the current JPA entities
 * (e.g. User#emailVerifiedAt, EmailVerificationToken, PasswordResetToken)
 * already require the V9-V11 schema. Booting the actual application with
 * Hibernate's {@code ddl-auto=validate} against a database intentionally
 * held at V8 fails schema validation before a single HTTP request could
 * ever be issued — that is not a historical-database scenario, it is a
 * broken-deployment scenario, and asserting through it would prove nothing.
 * A real historical database only ever has Flyway (never today's JPA model)
 * touch it while it sits below V9, so phase 1 mirrors that: a plain,
 * programmatic {@link Flyway} instance — configured exactly like the
 * application's own Flyway autoconfiguration, including the same
 * {@link LegacyBaselineFlywayCallback} that establishes the pre-Flyway
 * tables — migrated only to V8, with the two historical accounts seeded via
 * a raw JDBC connection. No Spring context, no Hibernate, exists yet at
 * this point.
 *
 * <p>Phase 2 then boots the real application exactly once, against that
 * same database, with no Flyway target restriction: V9-V11 apply for real,
 * Hibernate's {@code ddl-auto=validate} succeeds against the now-current
 * schema, and genuine HTTP POST /auth/generateToken calls prove both
 * historical accounts can actually log in.
 */
@Testcontainers(disabledWithoutDocker = true)
class HistoricalAccountsMigrationLoginTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    private static final String ADMIN_EMAIL = "admin-historique@assrone.ch";
    private static final String MEMBER_EMAIL = "membre-historique@assrone.ch";
    private static final String PASSWORD = "MotDePasseHistorique123";

    @Test
    void comptesAdminEtMembrePreexistantsSeConnectentApresMigrationV8VersV11() throws Exception {
        String encodedPassword = new BCryptPasswordEncoder().encode(PASSWORD);

        migrateToV8AndSeedHistoricalAccounts(encodedPassword);

        ConfigurableApplicationContext context = bootFullApplication();
        try {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true", Integer.class))
                    .as("V9-V11 applied for real on top of the pre-seeded V8 database")
                    .isEqualTo(11);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE email_verified_at IS NULL", Integer.class))
                    .as("both historical accounts backfilled — neither left locked out")
                    .isZero();
            assertThat(jdbc.queryForObject(
                    "SELECT role FROM users WHERE email = ?", String.class, ADMIN_EMAIL))
                    .as("role preserved across the migration")
                    .isEqualTo("ADMIN");
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'password_reset_tokens'",
                    Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'email_verification_tokens'",
                    Integer.class)).isEqualTo(1);

            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            HttpClient client = HttpClient.newHttpClient();
            ObjectMapper mapper = new ObjectMapper();

            for (String email : List.of(ADMIN_EMAIL, MEMBER_EMAIL)) {
                HttpRequest loginRequest = HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/auth/generateToken"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                        .build();
                HttpResponse<String> response = client.send(loginRequest, HttpResponse.BodyHandlers.ofString());

                assertThat(response.statusCode())
                        .as("login for pre-existing account " + email + " after V8->V11 migration"
                                + " — real password, no ddl-auto involvement")
                        .isEqualTo(200);
                assertThat(mapper.readTree(response.body()).get("token").asText()).isNotBlank();
            }
        } finally {
            context.close();
        }
    }

    private void migrateToV8AndSeedHistoricalAccounts(String encodedPassword) throws SQLException {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .callbacks(new LegacyBaselineFlywayCallback())
                .target("8")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            insertHistoricalUser(connection, ADMIN_EMAIL, "admin-historique", "ADMIN", encodedPassword);
            insertHistoricalUser(connection, MEMBER_EMAIL, "membre-historique", "USER", encodedPassword);
        }
    }

    private ConfigurableApplicationContext bootFullApplication() {
        return new SpringApplicationBuilder(BackendApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties(
                        "server.port=0",
                        "spring.datasource.url=" + postgres.getJdbcUrl(),
                        "spring.datasource.username=" + postgres.getUsername(),
                        "spring.datasource.password=" + postgres.getPassword())
                .run();
    }

    private void insertHistoricalUser(Connection connection, String email, String username, String role,
                                       String encodedPassword) throws SQLException {
        LocalDateTime historicalTimestamp = LocalDateTime.now().minusYears(2);
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO users (email, password, username, first_name, last_name, role, is_active,
                                    created_at, updated_at)
                VALUES (?, ?, ?, 'Jean', 'Dupont', ?, true, ?, ?)
                """)) {
            statement.setString(1, email);
            statement.setString(2, encodedPassword);
            statement.setString(3, username);
            statement.setString(4, role);
            statement.setObject(5, historicalTimestamp);
            statement.setObject(6, historicalTimestamp);
            statement.executeUpdate();
        }
    }
}
