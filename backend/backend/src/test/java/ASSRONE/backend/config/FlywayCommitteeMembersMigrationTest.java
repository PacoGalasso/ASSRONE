package ASSRONE.backend.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies V4__create_committee_members_table.sql against real Postgres via
 * Testcontainers: the table and index are created, and the seven committee
 * members previously hardcoded in the Angular "about" component are seeded
 * exactly once, in their original display order, with no fictional photo.
 *
 * Baselined at version 3 so only V4 is exercised, mirroring
 * FlywayEventsTypeCheckMigrationTest (baseline 1) and
 * FlywayDocumentsVisibilityMigrationTest (baseline 2). Flyway's
 * baselineOnMigrate only triggers baseline() "against a non-empty schema with
 * no schema history table" (see Flyway docs) — on a genuinely empty database
 * it is a no-op, and migrate() then runs from V1 for real, which fails here
 * since V1 expects membership_applications to already exist (in the real
 * application this is created by LegacyBaselineFlywayCallback, a
 * Spring-managed bean that this test's own isolated Flyway.configure()
 * instance never wires in). A minimal, unrelated marker table is created
 * first purely to make the schema non-empty so baselineOnMigrate actually
 * engages, exactly as the two sibling tests do by pre-creating the one real
 * table their own migration needs. V1/V2/V3 themselves are untouched and
 * still exercised in isolation by their own dedicated tests.
 */
@Testcontainers(disabledWithoutDocker = true)
class FlywayCommitteeMembersMigrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    private static final AtomicInteger DB_COUNTER = new AtomicInteger();

    private String createFreshDatabase() throws SQLException {
        String dbName = "committee_migration_test_" + DB_COUNTER.incrementAndGet();
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE DATABASE " + dbName);
        }
        return "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/" + dbName;
    }

    private void execute(String jdbcUrl, String sql) throws SQLException {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private String readSingleString(String jdbcUrl, String query) throws SQLException {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            rs.next();
            return rs.getString(1);
        }
    }

    /**
     * Makes the schema non-empty so baselineOnMigrate(true) actually baselines
     * at version 3 instead of silently no-op'ing and letting migrate() fall
     * through to running V1 for real (see class Javadoc). Unrelated to any
     * production table on purpose — V4 does not depend on any prior
     * migration's table, only on the schema not being completely empty.
     */
    private void prepareNonEmptySchemaForBaselining(String jdbcUrl) throws SQLException {
        execute(jdbcUrl, "CREATE TABLE flyway_baseline_probe (id INT)");
    }

    private long countRows(String jdbcUrl, String query) throws SQLException {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private List<String> orderedLastNames(String jdbcUrl) throws SQLException {
        List<String> names = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(jdbcUrl, postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT last_name FROM committee_members ORDER BY display_order ASC")) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        return names;
    }

    /**
     * Reads Béatrice's description directly out of V4's own SQL text on the
     * classpath and decodes the '' SQL-escaping, instead of a hand-transcribed
     * Java constant — a single source of truth immune to transcription
     * mistakes. Line endings are normalized (CRLF to LF) because a Windows
     * checkout of this repository (core.autocrlf) converts the migration
     * file's embedded newlines to CRLF, which Postgres then stores verbatim;
     * that is a line-ending artifact of the checkout, not truncation or data
     * loss, so both the expected and actual values are normalized the same
     * way before comparing.
     */
    private String readExpectedBeatriceDescription() throws IOException {
        String sql;
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("db/migration/V4__create_committee_members_table.sql")) {
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        int cursor = sql.indexOf("'Diplômée") + 1;
        StringBuilder decoded = new StringBuilder();
        while (true) {
            char c = sql.charAt(cursor);
            if (c == '\'') {
                if (cursor + 1 < sql.length() && sql.charAt(cursor + 1) == '\'') {
                    decoded.append('\'');
                    cursor += 2;
                    continue;
                }
                break;
            }
            decoded.append(c);
            cursor++;
        }
        return normalizeLineEndings(decoded.toString());
    }

    private String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n");
    }

    private Flyway flywayFor(String jdbcUrl) {
        return Flyway.configure()
                .dataSource(jdbcUrl, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("3")
                // Stops right after V4, the migration under test. Without this, migrate()
                // would keep applying every later migration on the classpath (V5+), which
                // targets a table (users) this test's minimal schema never creates.
                .target("4")
                .load();
    }

    @Test
    void migrationCreeLaTableEtEnsemenceLesSeptMembresDansLOrdreOriginal() throws Exception {
        String jdbcUrl = createFreshDatabase();
        prepareNonEmptySchemaForBaselining(jdbcUrl);

        flywayFor(jdbcUrl).migrate();

        assertThat(countRows(jdbcUrl, "SELECT COUNT(*) FROM committee_members")).isEqualTo(7);
        assertThat(countRows(jdbcUrl, "SELECT COUNT(*) FROM committee_members WHERE active = TRUE")).isEqualTo(7);
        assertThat(countRows(jdbcUrl, "SELECT COUNT(*) FROM committee_members WHERE photo_filename IS NOT NULL"))
                .isEqualTo(0);
        assertThat(orderedLastNames(jdbcUrl)).containsExactly(
                "Amoroso Galasso", "Da Silva", "Floch", "Gautier Meynier", "Schaller", "Santarelli", "à pourvoir");
        assertThat(countRows(jdbcUrl,
                "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'idx_committee_members_active_display_order'"))
                .isEqualTo(1);

        String beatriceDescription = normalizeLineEndings(readSingleString(jdbcUrl,
                "SELECT description FROM committee_members WHERE last_name = 'Amoroso Galasso'"));
        String expectedDescription = readExpectedBeatriceDescription();
        // Exact equality against the migration's own seed text (not a
        // hand-transcribed constant, not a hardcoded length) so any silent
        // truncation or corruption fails this assertion instead of passing on
        // a partial contains()/endsWith() match.
        assertThat(beatriceDescription).isEqualTo(expectedDescription);
        assertThat(beatriceDescription).hasSize(expectedDescription.length());
    }

    @Test
    void reexecuterLaMigrationNeDupliqueAucuneDonnee() throws SQLException {
        String jdbcUrl = createFreshDatabase();
        prepareNonEmptySchemaForBaselining(jdbcUrl);
        Flyway flyway = flywayFor(jdbcUrl);

        flyway.migrate();
        int secondRunCount = flyway.migrate().migrationsExecuted;

        assertThat(secondRunCount).isZero();
        assertThat(countRows(jdbcUrl, "SELECT COUNT(*) FROM committee_members")).isEqualTo(7);
        assertThat(countRows(jdbcUrl,
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '4' AND success = true"))
                .isEqualTo(1);
    }
}
