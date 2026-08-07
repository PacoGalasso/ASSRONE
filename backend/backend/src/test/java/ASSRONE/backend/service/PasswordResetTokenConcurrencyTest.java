package ASSRONE.backend.service;

import ASSRONE.backend.exception.InvalidPasswordResetTokenException;
import ASSRONE.backend.model.PasswordResetToken;
import ASSRONE.backend.model.User;
import ASSRONE.backend.repository.PasswordResetTokenRepository;
import ASSRONE.backend.repository.UserInfoRepository;
import ASSRONE.backend.security.SecureTokenGenerator;
import ASSRONE.backend.security.TokenHasher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the single-use guarantee documented on
 * PasswordResetTokenRepository#markUsedIfValid against real Postgres: two
 * concurrent resetPassword() calls presenting the exact same raw token must
 * never both succeed, and the DB must durably show the token claimed exactly
 * once and the password changed exactly once. Mirrors
 * SessionLimitConcurrencyTest's structure — real Testcontainers Postgres, a
 * non-@Transactional test class so each worker thread's commit is real and
 * visible to the other, independent REQUIRES_NEW transactions for setup and
 * verification.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PasswordResetTokenConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private UserInfoRepository userInfoRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    private TransactionTemplate requiresNew() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private void wipeTablesCommitted() {
        requiresNew().executeWithoutResult(status -> {
            jdbcTemplate.update("DELETE FROM password_reset_tokens");
            jdbcTemplate.update("DELETE FROM users");
        });
    }

    @BeforeEach
    void wipeBeforeEachScenario() {
        wipeTablesCommitted();
    }

    @AfterEach
    void wipeAfterEachScenario() {
        wipeTablesCommitted();
    }

    private record Setup(Long userId, String rawToken) {
    }

    private Setup createUserWithActiveTokenCommitted() {
        return requiresNew().execute(status -> {
            User user = userInfoRepository.save(User.builder()
                    .email("reset-concurrency@assrone.ch")
                    .username("reset-concurrency")
                    .password(passwordEncoder.encode("ancien-mot-de-passe"))
                    .role("USER")
                    .isActive(true)
                    .build());

            String rawToken = SecureTokenGenerator.generate();
            LocalDateTime now = LocalDateTime.now(clock);
            passwordResetTokenRepository.save(PasswordResetToken.builder()
                    .userId(user.getId())
                    .tokenHash(TokenHasher.sha256Hex(rawToken))
                    .createdAt(now)
                    .expiresAt(now.plusHours(1))
                    .build());

            return new Setup(user.getId(), rawToken);
        });
    }

    private record TaskOutcome(String label, String outcome) {
    }

    private TaskOutcome runResetPassword(CountDownLatch latch, String rawToken, String label) throws InterruptedException {
        latch.countDown();
        latch.await();
        try {
            passwordResetService.resetPassword(rawToken, "nouveauMotDePasse-" + label + "-123");
            return new TaskOutcome(label, "OK");
        } catch (InvalidPasswordResetTokenException ex) {
            return new TaskOutcome(label, "DENIED");
        }
    }

    @Test
    void deuxResetsSimultanesAvecLeMemeTokenNeReussissentJamaisTousLesDeux() throws Exception {
        Setup setup = createUserWithActiveTokenCommitted();

        CountDownLatch latch = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        TaskOutcome resultA;
        TaskOutcome resultB;
        try {
            Callable<TaskOutcome> resetA = () -> runResetPassword(latch, setup.rawToken(), "A");
            Callable<TaskOutcome> resetB = () -> runResetPassword(latch, setup.rawToken(), "B");

            Future<TaskOutcome> futureA = pool.submit(resetA);
            Future<TaskOutcome> futureB = pool.submit(resetB);
            resultA = futureA.get(30, TimeUnit.SECONDS);
            resultB = futureB.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        long okCount = java.util.stream.Stream.of(resultA, resultB).filter(r -> r.outcome().equals("OK")).count();
        long deniedCount = java.util.stream.Stream.of(resultA, resultB).filter(r -> r.outcome().equals("DENIED")).count();
        assertThat(okCount).as("exactly one of the two concurrent resets wins the race").isEqualTo(1);
        assertThat(deniedCount).as("the other loses and is denied").isEqualTo(1);

        // Independent verification transaction, raw JDBC.
        long usedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = ? AND used_at IS NOT NULL",
                Long.class, setup.userId());
        assertThat(usedCount).as("the token was claimed exactly once, never twice").isEqualTo(1);

        String storedHash = jdbcTemplate.queryForObject(
                "SELECT password FROM users WHERE id = ?", String.class, setup.userId());
        String winningLabel = resultA.outcome().equals("OK") ? "A" : "B";
        assertThat(passwordEncoder.matches("nouveauMotDePasse-" + winningLabel + "-123", storedHash))
                .as("the password on record matches exactly the winning thread's new password, not the loser's")
                .isTrue();
    }
}
