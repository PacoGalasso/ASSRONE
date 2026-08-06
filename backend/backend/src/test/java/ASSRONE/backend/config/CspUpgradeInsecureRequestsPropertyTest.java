package ASSRONE.backend.config;

import ASSRONE.backend.BackendApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the real application (same H2/no-web technique as ProductionStartupSafetyTest)
 * to prove app.security.csp.upgrade-insecure-requests resolves to the right value from
 * the real application.properties/application-production.properties layering — not
 * just that ContentSecurityPolicy#directives(boolean) behaves correctly given a boolean
 * (see ContentSecurityPolicyTest for that), but that the correct boolean is actually
 * produced per profile end to end.
 */
class CspUpgradeInsecureRequestsPropertyTest {

    // Deliberately NOT BackendApplicationTests'/application-local.properties' shared
    // test secret: ProductionSecurityGuard refuses that specific value under the
    // production profile (see ProductionSecurityGuardTest), and the production-profile
    // test below needs startup to actually succeed.
    private static final String VALID_JWT_SECRET = "dl7LgN70O7lO5bfvdzeP6w+S4qbEOnzqBrX2tBitUL37dVP7+8KiN1274N5jFGJZ";

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
                        // password from these SMTP_* env-var-style keys — see
                        // ProductionStartupSafetyTest for the full rationale.
                        "SMTP_HOST=localhost",
                        "SMTP_PORT=2525",
                        "SMTP_USERNAME=test@example.invalid",
                        "SMTP_PASSWORD=test-password-not-real",
                        "app.upload-dir=target/csp-property-test-uploads",
                        "app.contact.recipient=test@example.invalid"
                );
    }

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

    @Test
    void profilLocalNActivePasUpgradeInsecureRequestsParDefaut() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("spring.jpa.hibernate.ddl-auto", "update");
        properties.put("spring.flyway.baseline-on-migrate", "true");
        properties.put("app.jwt.secret", VALID_JWT_SECRET);
        SpringApplicationBuilder builder = baseBuilder("csp-property-local");

        runWithSystemProperties(builder, properties, context ->
                assertThat(context.getEnvironment().getProperty("app.security.csp.upgrade-insecure-requests", Boolean.class))
                        .isFalse());
    }

    @Test
    void profilProductionActiveUpgradeInsecureRequestsParDefaut() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("spring.jpa.hibernate.ddl-auto", "update");
        properties.put("spring.flyway.baseline-on-migrate", "true");
        properties.put("app.jwt.secret", VALID_JWT_SECRET);
        properties.put("app.security.refresh-cookie.secure", "true");
        properties.put("app.security.cors.allowed-origins", "https://assrone.ch");
        SpringApplicationBuilder builder = baseBuilder("csp-property-production").profiles("production");

        runWithSystemProperties(builder, properties, context ->
                assertThat(context.getEnvironment().getProperty("app.security.csp.upgrade-insecure-requests", Boolean.class))
                        .isTrue());
    }
}
