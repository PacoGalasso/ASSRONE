package ASSRONE.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Boots the real, full BackendApplication — not a slice, not a mock — under a mix of
 * safe and manifestly dangerous configurations, to prove the actual startup-time
 * refusal behavior described in RefreshCookieProperties/CorsProperties/
 * ProductionSecurityGuard, end to end. Uses a real H2 datasource and a Flyway
 * location with no migrations (same technique as BackendApplicationTests), so no
 * Docker/Postgres is required.
 *
 * Properties that also have a value in application.properties/
 * application-production.properties (ddl-auto, the refresh-cookie/CORS/JWT-secret
 * properties under test) are set as real JVM system properties rather than via
 * SpringApplicationBuilder#properties(...): the latter registers Spring Boot's
 * lowest-precedence "default properties", which a profile-specific properties file
 * always wins over — too low to actually override anything already set in
 * application.properties or application-production.properties. System properties
 * outrank both. Each test sets only the keys it varies and always clears them in a
 * finally block, since JVM system properties are process-global and would otherwise
 * leak into later tests/forks.
 */
class ProductionStartupSafetyTest {

    // 48 random bytes, Base64-encoded (HS384 key length required by JwtService).
    // Test-only signing key, never used outside this isolated test. Deliberately NOT
    // the same value as BackendApplicationTests'/application-local.properties' shared
    // test secret: ProductionSecurityGuard now refuses that specific value under the
    // production profile (see productionAvecSecretJwtDeTestConnuRefuseDeDemarrer below),
    // and every other test in this file needs production startup to actually succeed.
    private static final String VALID_JWT_SECRET = "dl7LgN70O7lO5bfvdzeP6w+S4qbEOnzqBrX2tBitUL37dVP7+8KiN1274N5jFGJZ";

    // The exact value committed in BackendApplicationTests/application-local.properties
    // (test scope) — public in this repository, so ProductionSecurityGuard refuses it
    // under the production profile regardless of its structural validity.
    private static final String KNOWN_TEST_JWT_SECRET = "zTjvaDrwlDQTMdHQ9vSfqXGwdkGSXJtT09uCOP+KLfO0RmyO617AZi/hK7VKCiKe";

    private static SpringApplicationBuilder baseBuilder(String dbName) {
        return new SpringApplicationBuilder(BackendApplication.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.datasource.url=jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1",
                        "spring.datasource.driver-class-name=org.h2.Driver",
                        "spring.datasource.username=sa",
                        "spring.datasource.password=",
                        "spring.flyway.locations=classpath:db/no-migrations-for-context-loads-test",
                        "spring.flyway.fail-on-missing-locations=false",
                        "spring.mail.host=localhost",
                        "spring.mail.port=2525",
                        "spring.mail.username=test@example.invalid",
                        "spring.mail.password=test-password-not-real",
                        // application-production.properties resolves spring.mail.host/
                        // password from these SMTP_* env-var-style keys (see there); the
                        // spring.mail.* keys just above cover the "local" profile scenario
                        // (application.properties' own ${SMTP_HOST:localhost}-style
                        // defaults), which never reads these SMTP_* keys at all.
                        "SMTP_HOST=localhost",
                        "SMTP_PORT=2525",
                        "SMTP_USERNAME=test@example.invalid",
                        "SMTP_PASSWORD=test-password-not-real",
                        "app.upload-dir=target/production-startup-safety-test-uploads",
                        "app.contact.recipient=test@example.invalid"
                );
    }

    /**
     * Runs the builder with the given system properties set (outranking
     * application.properties/application-production.properties for this one JVM-wide
     * property lookup), guaranteeing they are cleared afterwards either way.
     */
    private static void runWithSystemProperties(SpringApplicationBuilder builder, Map<String, String> systemProperties,
                                                 java.util.function.Consumer<ConfigurableApplicationContext> onSuccess) {
        systemProperties.forEach(System::setProperty);
        try {
            ConfigurableApplicationContext context = builder.run();
            try {
                onSuccess.accept(context);
            } finally {
                context.close();
            }
        } finally {
            systemProperties.keySet().forEach(System::clearProperty);
        }
    }

    private static Map<String, String> withJwtSecret(Map<String, String> properties) {
        properties.put("app.jwt.secret", VALID_JWT_SECRET);
        return properties;
    }

    private static Map<String, String> baseProductionProperties() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("spring.jpa.hibernate.ddl-auto", "update");
        // Explicit even though application.properties already sets this: even with no
        // pending migrations in this H2 test's empty Flyway location, Flyway still
        // fires LegacyBaselineFlywayCallback on BEFORE_MIGRATE, which creates
        // users/membership_applications/events/event_registrations/documents
        // directly — so Flyway still finds a non-empty schema with no history table
        // and needs this to auto-baseline instead of refusing.
        properties.put("spring.flyway.baseline-on-migrate", "true");
        return withJwtSecret(properties);
    }

    @Test
    void productionAvecCookieNonSecureRefuseDeDemarrer() {
        Map<String, String> properties = baseProductionProperties();
        properties.put("app.security.refresh-cookie.secure", "false");
        properties.put("app.security.cors.allowed-origins", "https://assrone.ch");
        SpringApplicationBuilder builder = baseBuilder("production-startup-safety-secure-false").profiles("production");

        assertThatThrownBy(() -> runWithSystemProperties(builder, properties, context -> { }))
                .isInstanceOf(BeanCreationException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause().hasMessageContaining("secure");
    }

    @Test
    void productionAvecOrigineCorsVideRefuseDeDemarrer() {
        Map<String, String> properties = baseProductionProperties();
        properties.put("app.security.refresh-cookie.secure", "true");
        properties.put("app.security.cors.allowed-origins", "");
        SpringApplicationBuilder builder = baseBuilder("production-startup-safety-cors-empty").profiles("production");

        assertThatThrownBy(() -> runWithSystemProperties(builder, properties, context -> { }))
                .isInstanceOf(BeanCreationException.class)
                .hasMessageContaining("app.security.cors");
    }

    @Test
    void productionAvecOrigineCorsWildcardRefuseDeDemarrer() {
        Map<String, String> properties = baseProductionProperties();
        properties.put("app.security.refresh-cookie.secure", "true");
        properties.put("app.security.cors.allowed-origins", "*");
        SpringApplicationBuilder builder = baseBuilder("production-startup-safety-cors-wildcard").profiles("production");

        assertThatThrownBy(() -> runWithSystemProperties(builder, properties, context -> { }))
                .isInstanceOf(BeanCreationException.class)
                .rootCause().hasMessageContaining("wildcard");
    }

    @Test
    void productionSansSecretJwtRefuseDeDemarrer() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("spring.jpa.hibernate.ddl-auto", "update");
        properties.put("app.jwt.secret", "");
        properties.put("app.security.refresh-cookie.secure", "true");
        properties.put("app.security.cors.allowed-origins", "https://assrone.ch");
        SpringApplicationBuilder builder = baseBuilder("production-startup-safety-no-jwt").profiles("production");

        assertThatThrownBy(() -> runWithSystemProperties(builder, properties, context -> { }))
                .isInstanceOf(BeanCreationException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause().hasMessageContaining("app.jwt.secret");
    }

    @Test
    void productionAvecSecretJwtDeTestConnuRefuseDeDemarrer() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("spring.jpa.hibernate.ddl-auto", "update");
        properties.put("spring.flyway.baseline-on-migrate", "true");
        properties.put("app.jwt.secret", KNOWN_TEST_JWT_SECRET);
        properties.put("app.security.refresh-cookie.secure", "true");
        properties.put("app.security.cors.allowed-origins", "https://assrone.ch");
        SpringApplicationBuilder builder = baseBuilder("production-startup-safety-known-secret").profiles("production");

        assertThatThrownBy(() -> runWithSystemProperties(builder, properties, context -> { }))
                .isInstanceOf(BeanCreationException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause().hasMessageContaining("app.jwt.secret");
    }

    @Test
    void productionAvecConfigurationValideDemarreCorrectement() {
        Map<String, String> properties = baseProductionProperties();
        properties.put("app.security.refresh-cookie.secure", "true");
        properties.put("app.security.cors.allowed-origins", "https://assrone.ch");
        SpringApplicationBuilder builder = baseBuilder("production-startup-safety-valid").profiles("production");

        runWithSystemProperties(builder, properties, context -> { });
    }

    @Test
    void localAvecCookieNonSecureDemarreCorrectement() {
        // Never override spring.profiles.active here: this must exercise the base
        // application.properties' own "local" default, not force a profile — the whole
        // point is proving ProductionSecurityGuard stays inactive under that default.
        Map<String, String> properties = withJwtSecret(new LinkedHashMap<>());
        properties.put("spring.jpa.hibernate.ddl-auto", "update");
        SpringApplicationBuilder builder = baseBuilder("production-startup-safety-local");

        runWithSystemProperties(builder, properties, context -> { });
    }
}
