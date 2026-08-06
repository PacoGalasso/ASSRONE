package ASSRONE.backend.config;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.api.callback.BaseCallback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import testsupport.DdlRecordingDataSource;

import javax.sql.DataSource;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The scenario the Docker validation that found the missing
 * event_registrations table was exercising: boot the real, full application
 * against a genuinely empty PostgreSQL database, with no pre-seeding of any
 * kind — no property overrides for spring.flyway.locations or
 * spring.jpa.hibernate.ddl-auto either, so this runs through the exact same
 * path production and every developer's first boot does. If
 * LegacyBaselineFlywayCallback is ever missing a table some entity needs, or
 * a real Flyway migration regresses, this is where it surfaces.
 *
 * The datasource bean is replaced with DdlRecordingDataSource so that every
 * SQL statement handed to the JDBC driver is captured, and a second Flyway
 * Callback bean marks the boundary the instant Flyway's own AFTER_MIGRATE
 * fires — letting demarrageEstProuveSansAucunDdlHibernate assert that
 * nothing recorded from that point on (i.e. while Hibernate was building and
 * validating its SessionFactory) is a CREATE/ALTER/DROP: proof that
 * ddl-auto=validate never mutates schema, not just an inference from the
 * context having started successfully.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class FreshDatabaseSchemaBootstrapTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        DdlRecordingDataSource.reset();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RecordingDataSourceConfig {

        @Bean
        @Primary
        DataSource dataSource(@Value("${spring.datasource.url}") String url,
                               @Value("${spring.datasource.username}") String username,
                               @Value("${spring.datasource.password}") String password) {
            DataSource real = DataSourceBuilder.create()
                    .url(url)
                    .username(username)
                    .password(password)
                    .driverClassName("org.postgresql.Driver")
                    .build();
            return new DdlRecordingDataSource(real);
        }

        @Bean
        FlywayCompletionMarkerCallback flywayCompletionMarkerCallback() {
            return new FlywayCompletionMarkerCallback();
        }
    }

    static class FlywayCompletionMarkerCallback extends BaseCallback {
        @Override
        public boolean supports(Event event, Context context) {
            return event == Event.AFTER_MIGRATE;
        }

        @Override
        public void handle(Event event, Context context) {
            DdlRecordingDataSource.markFlywayComplete();
        }
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private static final List<String> EXPECTED_TABLES = List.of(
            "users", "membership_applications", "events", "event_registrations",
            "documents", "committee_members", "refresh_tokens", "user_sessions",
            "password_reset_tokens", "email_verification_tokens");

    // V1..V8 are the pre-existing baseline (see LegacyBaselineFlywayCallback);
    // V9-V11 are this lot's own migrations. Listed explicitly, not just
    // counted, so a future migration that silently fails to apply (rather
    // than being absent) still fails this test on the missing version number,
    // not just on a stale count nobody remembered to update.
    private static final List<String> EXPECTED_MIGRATION_VERSIONS =
            List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11");

    @Test
    void demarrageSurBaseViergeReussitAvecToutesLesTablesEtContraintesAttendues() {
        assertThat(entityManagerFactory.isOpen()).isTrue();

        for (String table : EXPECTED_TABLES) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
                    Integer.class, table);
            assertThat(count).as("table " + table).isEqualTo(1);
        }

        List<String> appliedVersions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = true AND version IS NOT NULL ORDER BY installed_rank",
                String.class);
        assertThat(appliedVersions).as("every migration from V1 to V11 applied, in order, none skipped or failed")
                .containsExactlyElementsOf(EXPECTED_MIGRATION_VERSIONS);

        assertForeignKey("event_registrations", "events");
        assertForeignKey("refresh_tokens", "users");
        assertForeignKey("refresh_tokens", "user_sessions");
        assertForeignKey("user_sessions", "users");
        assertForeignKey("password_reset_tokens", "users");
        assertForeignKey("email_verification_tokens", "users");

        assertNotNullColumn("users", "email");
        assertNotNullColumn("event_registrations", "event_id");
        assertNotNullColumn("event_registrations", "normalized_email");
        assertNotNullColumn("refresh_tokens", "session_id");
        assertNotNullColumn("user_sessions", "public_id");
        assertNotNullColumn("documents", "stored_filename");

        // email_verified_at is deliberately nullable — see V9 (unverified is a
        // real, valid state for a newly registered account) — so this only
        // confirms the column exists, never that it's NOT NULL.
        assertColumnExists("users", "email_verified_at");

        assertNotNullColumn("password_reset_tokens", "user_id");
        assertNotNullColumn("password_reset_tokens", "token_hash");
        assertNotNullColumn("password_reset_tokens", "expires_at");
        assertUniqueIndex("password_reset_tokens", "uk_password_reset_tokens_token_hash");
        assertIndexExists("password_reset_tokens", "idx_password_reset_tokens_user_id");
        assertIndexExists("password_reset_tokens", "idx_password_reset_tokens_expires_at");

        assertNotNullColumn("email_verification_tokens", "user_id");
        assertNotNullColumn("email_verification_tokens", "token_hash");
        assertNotNullColumn("email_verification_tokens", "expires_at");
        assertUniqueIndex("email_verification_tokens", "uk_email_verification_tokens_token_hash");
        assertIndexExists("email_verification_tokens", "idx_email_verification_tokens_user_id");
        assertIndexExists("email_verification_tokens", "idx_email_verification_tokens_expires_at");
    }

    @Test
    void demarrageEstProuveSansAucunDdlHibernate() {
        entityManagerFactory.isOpen();

        List<String> afterFlyway = DdlRecordingDataSource.sqlRecordedAfterFlywayCompleted();
        List<String> ddlAfterFlyway = afterFlyway.stream()
                .map(sql -> sql.strip().toLowerCase(Locale.ROOT))
                .filter(sql -> sql.startsWith("create table") || sql.startsWith("alter table")
                        || sql.startsWith("drop table") || sql.startsWith("create sequence")
                        || sql.startsWith("drop sequence"))
                .toList();

        assertThat(ddlAfterFlyway).isEmpty();
    }

    private void assertForeignKey(String childTable, String referencedTable) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.table_constraints tc
                JOIN information_schema.constraint_column_usage ccu
                  ON tc.constraint_name = ccu.constraint_name AND tc.table_schema = ccu.table_schema
                WHERE tc.constraint_type = 'FOREIGN KEY'
                  AND tc.table_name = ?
                  AND ccu.table_name = ?
                """, Integer.class, childTable, referencedTable);
        assertThat(count).as(childTable + " -> " + referencedTable + " foreign key").isGreaterThanOrEqualTo(1);
    }

    private void assertNotNullColumn(String table, String column) {
        String isNullable = jdbcTemplate.queryForObject(
                "SELECT is_nullable FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
                String.class, table, column);
        assertThat(isNullable).as(table + "." + column + " nullability").isEqualTo("NO");
    }

    private void assertColumnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
                Integer.class, table, column);
        assertThat(count).as(table + "." + column + " exists").isEqualTo(1);
    }

    private void assertUniqueIndex(String table, String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE tablename = ? AND indexname = ? AND indexdef ILIKE 'CREATE UNIQUE%'",
                Integer.class, table, indexName);
        assertThat(count).as(table + "." + indexName + " unique index").isEqualTo(1);
    }

    private void assertIndexExists(String table, String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE tablename = ? AND indexname = ?",
                Integer.class, table, indexName);
        assertThat(count).as(table + "." + indexName + " index").isEqualTo(1);
    }
}
