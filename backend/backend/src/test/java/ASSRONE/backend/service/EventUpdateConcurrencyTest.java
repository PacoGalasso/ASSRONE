package ASSRONE.backend.service;

import ASSRONE.backend.dto.EventRegistrationRequest;
import ASSRONE.backend.dto.UpdateEventRequest;
import ASSRONE.backend.exception.EventCapacityConflictException;
import ASSRONE.backend.exception.EventFullException;
import ASSRONE.backend.model.Event;
import ASSRONE.backend.repository.EventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deliberately NOT @Transactional (class or method) — see AdminUserManagementConcurrencyTest
 * for the same reasoning. This class holds ONE Testcontainers PostgreSQL instance shared
 * across its 3 @Test methods (a Testcontainers @Container static field is per-class, not
 * per-method), so without cleanup between methods, one test's events/registrations leak
 * into the next — exactly what produced a global registration count of 4 instead of the
 * 2 a single scenario created. @BeforeEach/@AfterEach wipe both event tables in their own
 * committed transaction, verified by raw SQL, so every scenario starts and ends from a
 * deterministic, empty state regardless of method execution order.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class EventUpdateConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private EventService eventService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // EventService#register publishes an AFTER_COMMIT event that
    // EventRegistrationEmailListener forwards to EventEmailService, which would
    // otherwise open a real SMTP connection. The listener already absorbs any
    // RuntimeException from that call (covered by its own dedicated test), but a
    // genuine network attempt has no place in this concurrency test — mocking the
    // JavaMailSender bean makes send() a no-op without touching production wiring.
    @MockitoBean
    private JavaMailSender mailSender;

    private TransactionTemplate requiresNew() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private void wipeEventTablesCommitted() {
        requiresNew().executeWithoutResult(status -> {
            jdbcTemplate.update("DELETE FROM event_registrations");
            jdbcTemplate.update("DELETE FROM events");
        });
        long registrations = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM event_registrations", Long.class);
        long events = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM events", Long.class);
        assertThat(registrations).as("event_registrations must be empty after wipe").isZero();
        assertThat(events).as("events must be empty after wipe").isZero();
    }

    @BeforeEach
    void wipeBeforeEachScenario() {
        wipeEventTablesCommitted();
    }

    @AfterEach
    void wipeAfterEachScenario() {
        wipeEventTablesCommitted();
    }

    private static Event newEvent(int maxParticipants) {
        return Event.builder()
                .title("Atelier bénévolat")
                .description("Description")
                .type("Atelier")
                .eventDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(20, 0))
                .location("Local associatif")
                .maxParticipants(maxParticipants)
                .currentParticipants(0)
                .build();
    }

    private static UpdateEventRequest requestWithCapacity(Event event, int maxParticipants) {
        return UpdateEventRequest.builder()
                .title(event.getTitle())
                .description(event.getDescription())
                .type(event.getType())
                .eventDate(event.getEventDate())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .location(event.getLocation())
                .maxParticipants(maxParticipants)
                .build();
    }

    private void dumpDiagnostics(String label) {
        List<Map<String, Object>> events = jdbcTemplate.queryForList(
                "SELECT id, title, current_participants, max_participants FROM events ORDER BY id");
        List<Map<String, Object>> registrations = jdbcTemplate.queryForList(
                "SELECT event_id, email FROM event_registrations ORDER BY event_id, email");
        System.out.println("[DIAG " + label + "] events=" + events + " registrations=" + registrations);
    }

    private long countRegistrationsForEvent(Long eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_registrations WHERE event_id = ?", Long.class, eventId);
    }

    @Test
    void capaciteInferieureAuxInscritsRetourne409EtLEvenementResteInchange() {
        Event event = eventRepository.save(newEvent(2));
        eventService.register(event.getId(), EventRegistrationRequest.builder()
                .fullName("Alice A").email("alice@assrone.ch").build());
        eventService.register(event.getId(), EventRegistrationRequest.builder()
                .fullName("Bob B").email("bob@assrone.ch").build());

        dumpDiagnostics("avant-modification");
        // Explicit preconditions, scoped to this scenario's own event — never a
        // global count, which is exactly what leaked other tests' data before.
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM events", Long.class)).isEqualTo(1);
        assertThat(countRegistrationsForEvent(event.getId())).isEqualTo(2);
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getCurrentParticipants()).isEqualTo(2);

        assertThatThrownBy(() -> eventService.updateEvent(event.getId(), requestWithCapacity(event, 1)))
                .isInstanceOf(EventCapacityConflictException.class);

        dumpDiagnostics("apres-tentative-de-reduction");
        Event reloaded = eventRepository.findById(event.getId()).orElseThrow();
        assertThat(reloaded.getMaxParticipants()).isEqualTo(2);
        assertThat(reloaded.getCurrentParticipants()).isEqualTo(2);
        assertThat(countRegistrationsForEvent(event.getId())).isEqualTo(2);
    }

    @Test
    void capaciteAugmenteeReussitEtCapaciteReduiteMaisSuffisanteReussitAussi() {
        Event event = eventRepository.save(newEvent(5));
        eventService.register(event.getId(), EventRegistrationRequest.builder()
                .fullName("Alice A").email("alice2@assrone.ch").build());

        dumpDiagnostics("avant-modification");
        assertThat(countRegistrationsForEvent(event.getId())).isEqualTo(1);

        eventService.updateEvent(event.getId(), requestWithCapacity(event, 10));
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getMaxParticipants()).isEqualTo(10);

        eventService.updateEvent(event.getId(), requestWithCapacity(event, 1));
        dumpDiagnostics("apres-modifications");
        Event reloaded = eventRepository.findById(event.getId()).orElseThrow();
        assertThat(reloaded.getMaxParticipants()).isEqualTo(1);
        assertThat(reloaded.getCurrentParticipants()).isEqualTo(1);
        assertThat(countRegistrationsForEvent(event.getId())).isEqualTo(1);
    }

    @Test
    void inscriptionConcurrenteALaFermetureDeLaCapaciteNeLaisseJamaisCurrentSuperieurAMax() throws Exception {
        Event event = eventRepository.save(newEvent(2));
        eventService.register(event.getId(), EventRegistrationRequest.builder()
                .fullName("Alice A").email("alice3@assrone.ch").build());

        dumpDiagnostics("avant-concurrence");
        assertThat(countRegistrationsForEvent(event.getId())).isEqualTo(1);

        CountDownLatch latch = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Object registrationResult;
        Object editResult;
        try {
            Callable<Object> registerSecondParticipant = () -> {
                latch.countDown();
                latch.await();
                try {
                    return eventService.register(event.getId(), EventRegistrationRequest.builder()
                            .fullName("Bob B").email("bob3@assrone.ch").build());
                } catch (RuntimeException ex) {
                    return ex;
                }
            };
            Callable<Object> lowerCapacityToOne = () -> {
                latch.countDown();
                latch.await();
                try {
                    return eventService.updateEvent(event.getId(), requestWithCapacity(event, 1));
                } catch (RuntimeException ex) {
                    return ex;
                }
            };

            Future<Object> futureRegistration = pool.submit(registerSecondParticipant);
            Future<Object> futureEdit = pool.submit(lowerCapacityToOne);
            registrationResult = futureRegistration.get(30, TimeUnit.SECONDS);
            editResult = futureEdit.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        dumpDiagnostics("apres-concurrence");

        List<Object> results = List.of(registrationResult, editResult);
        long succes = results.stream().filter(r -> !(r instanceof Exception)).count();
        long echecsAttendus = results.stream()
                .filter(r -> r instanceof EventFullException || r instanceof EventCapacityConflictException)
                .count();

        // Exactly one of {registration, edit} succeeds and the other is refused —
        // never both succeed (which would leave currentParticipants > maxParticipants).
        assertThat(succes).isEqualTo(1);
        assertThat(echecsAttendus).isEqualTo(1);

        Event reloaded = eventRepository.findById(event.getId()).orElseThrow();
        assertThat(reloaded.getCurrentParticipants()).isLessThanOrEqualTo(reloaded.getMaxParticipants());
    }
}
