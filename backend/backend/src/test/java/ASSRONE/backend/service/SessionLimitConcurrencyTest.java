package ASSRONE.backend.service;

import ASSRONE.backend.model.User;
import ASSRONE.backend.model.UserSession;
import ASSRONE.backend.repository.UserInfoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that two simultaneous logins (SessionService#createSession calls)
 * for the same user never durably leave more than app.security.sessions.max-active
 * sessions active, even when both threads observe the count at exactly the
 * limit before either commits. Mirrors AdminUserManagementConcurrencyTest's
 * structure: real Testcontainers Postgres, a non-@Transactional test class so
 * each worker thread's commit is real and visible to the other, and
 * independent REQUIRES_NEW transactions (plus raw JDBC) for setup and
 * verification.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class SessionLimitConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private SessionService sessionService;

    @Autowired
    private UserInfoRepository userInfoRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    private static final int MAX_ACTIVE = 5;

    private TransactionTemplate requiresNew() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private void wipeUsersTableCommitted() {
        requiresNew().executeWithoutResult(status -> jdbcTemplate.update("DELETE FROM users"));
    }

    @BeforeEach
    void wipeBeforeEachScenario() {
        wipeUsersTableCommitted();
    }

    @AfterEach
    void wipeAfterEachScenario() {
        wipeUsersTableCommitted();
    }

    private User createUserCommitted() {
        return requiresNew().execute(status -> userInfoRepository.save(User.builder()
                .email("membre-limite@assrone.ch")
                .username("membre-limite")
                .password("hash")
                .role("USER")
                .isActive(true)
                .build()));
    }

    /** Fills the user up to exactly MAX_ACTIVE already-active sessions, oldest first by lastUsedAt. */
    private void fillToLimitCommitted(Long userId) {
        requiresNew().executeWithoutResult(status -> {
            for (int i = 0; i < MAX_ACTIVE; i++) {
                LocalDateTime lastUsed = LocalDateTime.now(clock).minusHours(MAX_ACTIVE - i);
                jdbcTemplate.update("""
                        INSERT INTO user_sessions (public_id, user_id, created_at, last_used_at, expires_at)
                        VALUES (?, ?, ?, ?, ?)
                        """, "pre-existing-" + i, userId, lastUsed, lastUsed, LocalDateTime.now(clock).plusDays(7));
            }
        });
    }

    private record TaskOutcome(String label, String outcome) {
    }

    private TaskOutcome runCreateSession(CountDownLatch latch, Long userId, String label) throws InterruptedException {
        latch.countDown();
        latch.await();
        try {
            sessionService.createSession(userId, LocalDateTime.now(clock).plusDays(7), "127.0.0." + label, "UA-" + label);
            return new TaskOutcome(label, "OK");
        } catch (RuntimeException ex) {
            return new TaskOutcome(label, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    @Test
    void deuxLoginsSimultanesALaLimiteNeDepassentJamaisDurablementLaLimiteConfiguree() throws Exception {
        User user = createUserCommitted();
        fillToLimitCommitted(user.getId());

        long preconditionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_sessions WHERE user_id = ? AND revoked_at IS NULL",
                Long.class, user.getId());
        assertThat(preconditionCount).as("precondition: exactly at the limit before the concurrent logins").isEqualTo(MAX_ACTIVE);

        CountDownLatch latch = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<TaskOutcome> loginA = () -> runCreateSession(latch, user.getId(), "A");
            Callable<TaskOutcome> loginB = () -> runCreateSession(latch, user.getId(), "B");

            Future<TaskOutcome> futureA = pool.submit(loginA);
            Future<TaskOutcome> futureB = pool.submit(loginB);
            TaskOutcome resultA = futureA.get(30, TimeUnit.SECONDS);
            TaskOutcome resultB = futureB.get(30, TimeUnit.SECONDS);

            assertThat(resultA.outcome()).isEqualTo("OK");
            assertThat(resultB.outcome()).isEqualTo("OK");
        } finally {
            pool.shutdownNow();
        }

        // Independent verification transaction, raw JDBC — never trusts a
        // JPA-level cache from either worker thread's own committed session.
        long activeCountAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_sessions WHERE user_id = ? AND revoked_at IS NULL",
                Long.class, user.getId());
        assertThat(activeCountAfter)
                .as("two concurrent logins at the limit must never durably leave more than max-active sessions")
                .isEqualTo(MAX_ACTIVE);

        long totalRowsAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_sessions WHERE user_id = ?", Long.class, user.getId());
        assertThat(totalRowsAfter).as("2 new sessions created, 2 oldest revoked, none deleted").isEqualTo(MAX_ACTIVE + 2);

        long revokedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_sessions WHERE user_id = ? AND revoked_at IS NOT NULL AND revocation_reason = 'SESSION_LIMIT_ENFORCED'",
                Long.class, user.getId());
        assertThat(revokedCount).as("exactly the 2 oldest sessions were evicted, each audited via SESSION_LIMIT_ENFORCED")
                .isEqualTo(2);

        List<String> stillActivePublicIds = requiresNew().execute(status ->
                sessionService.listActiveSessions(user.getId()).stream().map(UserSession::getPublicId).toList());
        assertThat(stillActivePublicIds).doesNotContain("pre-existing-0", "pre-existing-1");
    }
}
