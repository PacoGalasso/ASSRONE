package ASSRONE.backend.service;

import ASSRONE.backend.exception.LastAdministratorException;
import ASSRONE.backend.model.User;
import ASSRONE.backend.model.UserRole;
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deliberately NOT @Transactional (class or method): a Spring-test-managed
 * transaction wrapping the whole method would (a) hide the two worker threads'
 * commits from each other under a rollback-at-end sandbox, and (b) make this
 * test's own repository reads use a connection/snapshot that never actually
 * observes what PostgreSQL persisted. Setup, the two concurrent operations, and
 * final verification each run in their own independent, fully committed
 * transaction — verification reads go through both the JPA repository AND raw
 * JDBC (a completely separate connection) so a JPA-level staleness bug and a
 * genuine database-state bug cannot be confused with each other.
 *
 * The `users` table belongs exclusively to this test class's Testcontainers
 * PostgreSQL instance (no other test shares this container), so wiping it
 * completely before and after every test — in its own committed transaction,
 * verified by raw SQL — is safe and is what makes each scenario deterministic
 * regardless of execution order: a prior test's surviving row (e.g. a
 * deliberately-not-deleted "last admin") can never leak into the next
 * scenario's admin count.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class AdminUserManagementConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private AdminUserManagementService adminUserManagementService;

    @Autowired
    private UserInfoRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TransactionTemplate requiresNew() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private void wipeUsersTableCommitted() {
        requiresNew().executeWithoutResult(status -> jdbcTemplate.update("DELETE FROM users"));
        long remaining = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        assertThat(remaining).as("users table must be empty after wipe").isZero();
    }

    @BeforeEach
    void wipeUsersTableBeforeEachScenario() {
        wipeUsersTableCommitted();
    }

    @AfterEach
    void wipeUsersTableAfterEachScenario() {
        wipeUsersTableCommitted();
    }

    private User createAdminCommitted(String email) {
        return requiresNew().execute(status -> repository.save(User.builder()
                .email(email)
                .username(email)
                .password("hash")
                .role("ADMIN")
                .isActive(true)
                .build()));
    }

    private record TaskOutcome(String actor, Long targetId, String requestedRole, String outcome) {
    }

    private void dumpDiagnostics(String label) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT id, email, role FROM users ORDER BY id");
        Long adminCountSql = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'", Long.class);
        System.out.println("[DIAG " + label + "] users=" + rows + " adminCountSql=" + adminCountSql);
    }

    private void assertExactlyTwoAdminsPrecondition() {
        long totalUsers = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        long adminCountSql = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'", Long.class);
        assertThat(totalUsers).as("precondition: exactly the 2 users this scenario created").isEqualTo(2);
        assertThat(adminCountSql).as("precondition: exactly 2 admins before the concurrent run").isEqualTo(2);
    }

    @Test
    void deuxRetrogradationsConcurrentesDesDeuxDerniersAdminsNeLaissentJamaisZeroAdmin() throws Exception {
        User admin1 = createAdminCommitted("admin1@assrone.ch");
        User admin2 = createAdminCommitted("admin2@assrone.ch");
        dumpDiagnostics("apres-setup");
        assertExactlyTwoAdminsPrecondition();

        CountDownLatch latch = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<TaskOutcome> demoteAdmin2ByAdmin1 = () -> runChangeRole(latch, admin1.getEmail(), admin2.getId());
            Callable<TaskOutcome> demoteAdmin1ByAdmin2 = () -> runChangeRole(latch, admin2.getEmail(), admin1.getId());

            Future<TaskOutcome> futureA = pool.submit(demoteAdmin2ByAdmin1);
            Future<TaskOutcome> futureB = pool.submit(demoteAdmin1ByAdmin2);
            TaskOutcome resultA = futureA.get(30, TimeUnit.SECONDS);
            TaskOutcome resultB = futureB.get(30, TimeUnit.SECONDS);
            System.out.println("[DIAG resultA] " + resultA);
            System.out.println("[DIAG resultB] " + resultB);

            List<TaskOutcome> results = List.of(resultA, resultB);
            long succes = results.stream().filter(r -> "OK".equals(r.outcome())).count();
            long refus = results.stream().filter(r -> "LastAdministratorException".equals(r.outcome())).count();

            assertThat(results).hasSize(2);
            assertThat(succes).isEqualTo(1);
            assertThat(refus).isEqualTo(1);
            assertThat(succes + refus).isEqualTo(results.size());
        } finally {
            pool.shutdownNow();
        }

        dumpDiagnostics("apres-threads");

        // Independent verification transaction: no entity cached from setup or
        // from the worker threads (each of those ran in its own, already-committed
        // or already-rolled-back transaction on its own connection).
        Set<String> finalRoles = requiresNew().execute(status -> {
            String role1 = repository.findById(admin1.getId()).orElseThrow().getRole();
            String role2 = repository.findById(admin2.getId()).orElseThrow().getRole();
            return Set.of(role1, role2);
        });
        assertThat(finalRoles).isEqualTo(Set.of("ADMIN", "USER"));

        long adminCountSql = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'", Long.class);
        assertThat(adminCountSql).isEqualTo(1);
    }

    private TaskOutcome runChangeRole(CountDownLatch latch, String actorEmail, Long targetId) throws InterruptedException {
        latch.countDown();
        latch.await();
        try {
            adminUserManagementService.changeRole(actorEmail, targetId, UserRole.USER);
            return new TaskOutcome(actorEmail, targetId, "USER", "OK");
        } catch (LastAdministratorException ex) {
            return new TaskOutcome(actorEmail, targetId, "USER", "LastAdministratorException");
        } catch (RuntimeException ex) {
            return new TaskOutcome(actorEmail, targetId, "USER", ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    @Test
    void deuxSuppressionsConcurrentesDesDeuxDerniersAdminsNeSupprimentJamaisTousLesAdmins() throws Exception {
        User admin1 = createAdminCommitted("admin3@assrone.ch");
        User admin2 = createAdminCommitted("admin4@assrone.ch");
        dumpDiagnostics("apres-setup-suppression");
        assertExactlyTwoAdminsPrecondition();

        CountDownLatch latch = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<TaskOutcome> deleteAdmin2ByAdmin1 = () -> runDeleteUser(latch, admin1.getEmail(), admin2.getId());
            Callable<TaskOutcome> deleteAdmin1ByAdmin2 = () -> runDeleteUser(latch, admin2.getEmail(), admin1.getId());

            Future<TaskOutcome> futureA = pool.submit(deleteAdmin2ByAdmin1);
            Future<TaskOutcome> futureB = pool.submit(deleteAdmin1ByAdmin2);
            TaskOutcome resultA = futureA.get(30, TimeUnit.SECONDS);
            TaskOutcome resultB = futureB.get(30, TimeUnit.SECONDS);
            System.out.println("[DIAG resultA] " + resultA);
            System.out.println("[DIAG resultB] " + resultB);

            List<TaskOutcome> results = List.of(resultA, resultB);
            long succes = results.stream().filter(r -> "OK".equals(r.outcome())).count();
            long refus = results.stream().filter(r -> "LastAdministratorException".equals(r.outcome())).count();

            assertThat(results).hasSize(2);
            assertThat(succes).isEqualTo(1);
            assertThat(refus).isEqualTo(1);
            assertThat(succes + refus).isEqualTo(results.size());
        } finally {
            pool.shutdownNow();
        }

        dumpDiagnostics("apres-threads-suppression");

        boolean[] existence = requiresNew().execute(status -> new boolean[]{
                repository.findById(admin1.getId()).isPresent(),
                repository.findById(admin2.getId()).isPresent()
        });
        boolean admin1Exists = existence[0];
        boolean admin2Exists = existence[1];
        assertThat(admin1Exists ^ admin2Exists).as("exactly one of the two must still exist").isTrue();

        String survivingRole = requiresNew().execute(status -> admin1Exists
                ? repository.findById(admin1.getId()).orElseThrow().getRole()
                : repository.findById(admin2.getId()).orElseThrow().getRole());
        assertThat(survivingRole).isEqualTo("ADMIN");

        long totalRemaining = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        long adminCountSql = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'", Long.class);
        assertThat(totalRemaining).isEqualTo(1);
        assertThat(adminCountSql).isEqualTo(1);
    }

    private TaskOutcome runDeleteUser(CountDownLatch latch, String actorEmail, Long targetId) throws InterruptedException {
        latch.countDown();
        latch.await();
        try {
            adminUserManagementService.deleteUser(actorEmail, targetId);
            return new TaskOutcome(actorEmail, targetId, "-", "OK");
        } catch (LastAdministratorException ex) {
            return new TaskOutcome(actorEmail, targetId, "-", "LastAdministratorException");
        } catch (RuntimeException ex) {
            return new TaskOutcome(actorEmail, targetId, "-", ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }
}
