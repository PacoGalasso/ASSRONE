package ASSRONE.backend.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * A real, unaltered Spring context boot (H2, no property overrides beyond
 * what every test in this module already gets from
 * src/test/resources/application-local.properties — the same "local"
 * profile a plain {@code mvn spring-boot:run} loads), not a unit test that
 * hand-builds AccountLifecycleProperties. This is the exact configuration
 * chain (base application.properties defaults, layered profile file,
 * @ConfigurationProperties binding, then AccountLifecycleEmailService's own
 * link construction) a real local developer's backend actually runs
 * through — the only way to conclusively rule out a stale/overridden
 * frontend-url ever silently resurrecting a /auth/reset-password link
 * again, which is exactly what a plain unit test mocking the properties
 * away cannot catch. See AccountLifecycleEmailServiceTest for the
 * lower-level, per-scenario link-format unit coverage this complements.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:account-lifecycle-real-config-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.flyway.locations=classpath:db/no-migrations-for-context-loads-test",
        "spring.flyway.fail-on-missing-locations=false",
        "app.jwt.secret=zTjvaDrwlDQTMdHQ9vSfqXGwdkGSXJtT09uCOP+KLfO0RmyO617AZi/hK7VKCiKe",
        "app.upload-dir=target/account-lifecycle-real-config-test-uploads",
        "app.contact.recipient=test@example.invalid",
})
class AccountLifecycleEmailServiceRealConfigIntegrationTest {

    @Autowired
    private AccountLifecycleEmailService accountLifecycleEmailService;

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    void leLienDeReinitialisationConstruitParLeVraiContexteSpringNeContientJamaisAuth() {
        accountLifecycleEmailService.sendPasswordResetEmail("membre@assrone.ch", "un-token-de-test");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        String body = captor.getValue().getText();

        assertThat(body)
                .as("chaîne de configuration réelle (application.properties -> AccountLifecycleProperties "
                        + "-> AccountLifecycleEmailService), pas une propriété reconstruite à la main")
                .contains("http://localhost:4200/reset-password?token=un-token-de-test")
                .doesNotContain("/auth/reset-password");
    }

    @Test
    void leLienDeVerificationConstruitParLeVraiContexteSpringNeContientJamaisAuth() {
        accountLifecycleEmailService.sendVerificationEmail("membre@assrone.ch", "un-autre-token");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        String body = captor.getValue().getText();

        assertThat(body)
                .contains("http://localhost:4200/verify-email?token=un-autre-token")
                .doesNotContain("/auth/verify-email");
    }
}
