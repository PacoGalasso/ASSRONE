package ASSRONE.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies the full Spring context wires up correctly — bean graph, JPA/Hibernate
 * entity mappings, Spring Security filter chain, JWT signing key, mail sender,
 * rate limiting, etc. — without depending on any real external service.
 *
 * Flyway's migrations are skipped here only: they are Postgres-specific (V1
 * uses a PL/pgSQL DO $$ ... END $$; block, unsupported by H2 even in
 * PostgreSQL compatibility mode) and are already exercised end-to-end
 * against real Postgres by their own dedicated Testcontainers tests
 * (FlywayMembershipApplicationMigrationTest, FlywayEventsTypeCheckMigrationTest,
 * FlywayDocumentsVisibilityMigrationTest, FlywayCommitteeMembersMigrationTest).
 * Rather than disabling Flyway outright (spring.flyway.enabled=false, which
 * removes the Flyway bean and breaks FlywayStartupMigrator's constructor
 * injection), spring.flyway.locations is pointed at a classpath folder that
 * doesn't exist: the Flyway bean still exists and FlywayStartupMigrator still
 * runs on ApplicationReadyEvent exactly as in production, it just finds zero
 * migrations to apply, so no Postgres-specific SQL is ever executed. With no
 * migrations to run, Hibernate's own ddl-auto=update becomes the sole schema
 * owner for this context, which is generic enough to run against the
 * in-memory H2 database used here — so this test needs neither Docker nor a
 * real Postgres instance, anywhere, ever.
 *
 * All values below are dummy/test-only and scoped to this single test class
 * via @SpringBootTest(properties = ...) — they never apply to any other test
 * or to production, and nothing here weakens Spring Security (the real
 * filter chain, JWT filter and rate limiter all still load normally; only
 * their external dependencies — datasource, Flyway migration source, mail
 * server — are swapped for self-contained equivalents).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:backend-application-tests;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.locations=classpath:db/no-migrations-for-context-loads-test",
        "spring.flyway.fail-on-missing-locations=false",
        // 48 random bytes, Base64-encoded (HS384 key length required by JwtService).
        // Test-only signing key, never used outside this isolated context.
        "app.jwt.secret=zTjvaDrwlDQTMdHQ9vSfqXGwdkGSXJtT09uCOP+KLfO0RmyO617AZi/hK7VKCiKe",
        "spring.mail.host=localhost",
        "spring.mail.port=2525",
        "spring.mail.username=test@example.invalid",
        "spring.mail.password=test-password-not-real",
        "app.upload-dir=target/context-loads-test-uploads",
        "app.contact.recipient=test@example.invalid",
})
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
