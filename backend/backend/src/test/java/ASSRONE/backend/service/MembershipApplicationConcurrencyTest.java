package ASSRONE.backend.service;

import ASSRONE.backend.dto.CreateMembershipApplicationRequest;
import ASSRONE.backend.exception.MembershipApplicationAlreadyPendingException;
import ASSRONE.backend.model.ApplicationStatus;
import ASSRONE.backend.model.MembershipApplication;
import ASSRONE.backend.model.MembershipType;
import ASSRONE.backend.repository.MembershipApplicationRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class MembershipApplicationConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MembershipApplicationService membershipApplicationService;

    @Autowired
    private MembershipApplicationRepository membershipApplicationRepository;

    private static CreateMembershipApplicationRequest requete(String email) {
        return CreateMembershipApplicationRequest.builder()
                .fullName("Jean Dupont")
                .email(email)
                .membershipType(MembershipType.INDIVIDUEL)
                .charterAccepted(true)
                .build();
    }

    private static MembershipApplication candidaturePendingBrute(String email) {
        return MembershipApplication.builder()
                .fullName("Jean Dupont")
                .email(email)
                .membershipType(MembershipType.INDIVIDUEL)
                .charterAccepted(true)
                .status(ApplicationStatus.PENDING)
                .build();
    }

    @Test
    void deuxSoumissionsSimultaneesAvecLeMemeEmailNormaliseUneSeuleReussit() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Object> taskA = () -> {
                latch.countDown();
                latch.await();
                try {
                    return membershipApplicationService.submit(requete("concurrence@assrone.ch"));
                } catch (RuntimeException ex) {
                    return ex;
                }
            };
            Callable<Object> taskB = () -> {
                latch.countDown();
                latch.await();
                try {
                    return membershipApplicationService.submit(requete("  Concurrence@ASSRONE.ch  "));
                } catch (RuntimeException ex) {
                    return ex;
                }
            };

            Future<Object> futureA = pool.submit(taskA);
            Future<Object> futureB = pool.submit(taskB);
            Object resultA = futureA.get(30, TimeUnit.SECONDS);
            Object resultB = futureB.get(30, TimeUnit.SECONDS);

            List<Object> results = List.of(resultA, resultB);
            long succes = results.stream().filter(r -> !(r instanceof Exception)).count();
            long doublons = results.stream().filter(MembershipApplicationAlreadyPendingException.class::isInstance).count();

            assertThat(succes).isEqualTo(1);
            assertThat(doublons).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(membershipApplicationRepository.existsByEmailAndStatus("concurrence@assrone.ch", ApplicationStatus.PENDING))
                .isTrue();
        long lignesPending = membershipApplicationRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(a -> a.getEmail().equals("concurrence@assrone.ch"))
                .filter(a -> a.getStatus() == ApplicationStatus.PENDING)
                .count();
        assertThat(lignesPending).isEqualTo(1);
    }

    @Test
    void nouvelleSoumissionAutoriseeApresRejetDeLaPremiere() {
        var premiere = membershipApplicationService.submit(requete("reapplique@assrone.ch"));
        var application = membershipApplicationRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(a -> a.getEmail().equals("reapplique@assrone.ch"))
                .findFirst()
                .orElseThrow();
        application.setStatus(ApplicationStatus.REJECTED);
        membershipApplicationRepository.saveAndFlush(application);

        var seconde = membershipApplicationService.submit(requete("reapplique@assrone.ch"));

        assertThat(premiere).isNotNull();
        assertThat(seconde).isNotNull();
        long lignesPending = membershipApplicationRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(a -> a.getEmail().equals("reapplique@assrone.ch"))
                .filter(a -> a.getStatus() == ApplicationStatus.PENDING)
                .count();
        assertThat(lignesPending).isEqualTo(1);
    }

    @Test
    void lesVariantesDeCasseEtDespacesCollisionnentAuNiveauPostgres() {
        // Entities are inserted directly via the repository, bypassing
        // MembershipApplicationService's own Java-side normalization, so this
        // proves the protection is enforced by the PostgreSQL index itself.
        membershipApplicationRepository.saveAndFlush(candidaturePendingBrute("person.postgres@example.com"));

        assertThatThrownBy(() -> membershipApplicationRepository.saveAndFlush(
                candidaturePendingBrute("PERSON.POSTGRES@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> membershipApplicationRepository.saveAndFlush(
                candidaturePendingBrute("  person.postgres@example.com  ")))
                .isInstanceOf(DataIntegrityViolationException.class);

        long lignesPending = membershipApplicationRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(a -> a.getEmail().trim().equalsIgnoreCase("person.postgres@example.com"))
                .filter(a -> a.getStatus() == ApplicationStatus.PENDING)
                .count();
        assertThat(lignesPending).isEqualTo(1);
    }

    @Test
    void laCollisionEstRapporteeAvecLeNomDeContrainteAttenduParLeService() {
        membershipApplicationRepository.saveAndFlush(candidaturePendingBrute("contrainte@example.com"));

        assertThatThrownBy(() -> membershipApplicationRepository.saveAndFlush(
                candidaturePendingBrute("CONTRAINTE@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> {
                    Throwable cause = ex.getCause();
                    assertThat(cause).isInstanceOf(ConstraintViolationException.class);
                    assertThat(((ConstraintViolationException) cause).getConstraintName())
                            .isEqualToIgnoringCase("uk_membership_application_pending_email");
                });
    }
}
