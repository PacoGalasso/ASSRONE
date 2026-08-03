package ASSRONE.backend.service;

import ASSRONE.backend.dto.EventRegistrationRequest;
import ASSRONE.backend.exception.EventFullException;
import ASSRONE.backend.model.Event;
import ASSRONE.backend.model.EventType;
import ASSRONE.backend.repository.EventRegistrationRepository;
import ASSRONE.backend.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class EventRegistrationConcurrencyTest {

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
    private EventRegistrationRepository eventRegistrationRepository;

    @Test
    void deuxInscriptionsSimultaneesSurUneSeulePlaceUneSeuleReussit() throws Exception {
        Event event = eventRepository.save(Event.builder()
                .title("Atelier bénévolat")
                .type(EventType.ATELIER)
                .eventDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(20, 0))
                .location("Local associatif")
                .maxParticipants(1)
                .currentParticipants(0)
                .build());

        EventRegistrationRequest requestA = EventRegistrationRequest.builder()
                .fullName("Alice A")
                .email("alice@assrone.ch")
                .build();
        EventRegistrationRequest requestB = EventRegistrationRequest.builder()
                .fullName("Bob B")
                .email("bob@assrone.ch")
                .build();

        CountDownLatch latch = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Object> taskA = () -> {
                latch.countDown();
                latch.await();
                try {
                    return eventService.register(event.getId(), requestA);
                } catch (RuntimeException ex) {
                    return ex;
                }
            };
            Callable<Object> taskB = () -> {
                latch.countDown();
                latch.await();
                try {
                    return eventService.register(event.getId(), requestB);
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
            long echecsCapacite = results.stream().filter(EventFullException.class::isInstance).count();

            assertThat(succes).isEqualTo(1);
            assertThat(echecsCapacite).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        Event recharge = eventRepository.findById(event.getId()).orElseThrow();
        assertThat(recharge.getCurrentParticipants()).isEqualTo(1);
        assertThat(eventRegistrationRepository.count()).isEqualTo(1);
    }
}
