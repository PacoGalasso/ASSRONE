package ASSRONE.backend.controller;

import ASSRONE.backend.BackendApplication;
import ASSRONE.backend.model.User;
import ASSRONE.backend.repository.UserInfoRepository;
import ASSRONE.backend.service.LoginAttemptService;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import testsupport.LoginAttemptSecondContextTestConfig;

import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real HTTP requests through the full Spring Security filter chain (LOT 7a rate
 * limiting + JwtAuthFilter + the real DaoAuthenticationProvider) against real
 * PostgreSQL. Each test uses a distinct remote IP so LOT 7a's per-IP LOGIN
 * bucket (10/min) never interferes with the account-level assertions below,
 * except the one test dedicated to proving that interaction.
 *
 * classes = BackendApplication.class is set explicitly as defense in depth
 * against Spring Boot's test bootstrapper auto-detecting some other
 * configuration and using it AS the primary context's configuration instead of
 * BackendApplication — silently dropping PasswordConfig/SecurityConfig/component
 * scanning. That exact failure mode has bitten this test twice already: first
 * via a nested @Configuration class, then via testsupport.LoginAttemptSecondContextTestConfig
 * living under ASSRONE.backend (BackendApplication's implicit @ComponentScan
 * root) and getting picked up from there even as a plain top-level class. That
 * config now lives in the "testsupport" package specifically because it is NOT
 * a sub-package of ASSRONE.backend, so BackendApplication's component scan
 * cannot reach it regardless of nesting or annotations.
 *
 * "Multiple instances" is approximated by a second, fully independent Spring
 * context (its own DataSource/EntityManagerFactory/PlatformTransactionManager,
 * see testsupport.LoginAttemptSecondContextTestConfig) pointed at the same
 * PostgreSQL container — not a second JVM, but a real second Spring context,
 * proving lockout state lives in PostgreSQL, not in any single component's
 * memory.
 */
@SpringBootTest(classes = BackendApplication.class)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class LoginLockoutIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final String PASSWORD = "MotDePasseValide123";
    private static final String GENERIC_BODY = "{\"error\":\"Email ou mot de passe incorrect.\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserInfoRepository userInfoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Clock clock;

    // This class exercises the login-lockout mechanism, not email
    // verification — every seeded account here represents a pre-existing,
    // already-verified account (the scenario V9's backfill exists for), so
    // emailVerifiedAt is set explicitly. A real new registration is
    // deliberately NOT auto-verified this way (see UserInfoService#addUser);
    // this fixture never simulates that flow.
    private String seedAccount(String email) {
        userInfoRepository.findByEmail(email).ifPresent(userInfoRepository::delete);
        userInfoRepository.saveAndFlush(User.builder()
                .email(email)
                .username(email)
                .firstName("Jean")
                .lastName("Dupont")
                .password(passwordEncoder.encode(PASSWORD))
                .role("USER")
                .isActive(true)
                .emailVerifiedAt(LocalDateTime.now(clock))
                .build());
        return email;
    }

    private MockHttpServletRequestBuilder loginRequest(String email, String password, String ip) {
        return post("/auth/generateToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                });
    }

    @Test
    void quatreEchecsNeVerrouillentPasLeCompte() throws Exception {
        String email = seedAccount("lockout-1@assrone.ch");

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(loginRequest(email, "mauvais-mot-de-passe", "40.0.0.1"))
                    .andExpect(status().isUnauthorized());
        }

        User user = userInfoRepository.findByEmail(email).orElseThrow();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(4);
        assertThat(user.getLockedUntil()).isNull();

        mockMvc.perform(loginRequest(email, PASSWORD, "40.0.0.1"))
                .andExpect(status().isOk());
    }

    @Test
    void cinquiemeEchecVerrouilleLeCompteQuinzeMinutes() throws Exception {
        String email = seedAccount("lockout-2@assrone.ch");
        LocalDateTime avant = LocalDateTime.now(Clock.systemDefaultZone());

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(loginRequest(email, "mauvais-mot-de-passe", "40.0.0.2"))
                    .andExpect(status().isUnauthorized());
        }

        User user = userInfoRepository.findByEmail(email).orElseThrow();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(user.getLockedUntil()).isNotNull();
        assertThat(user.getLockedUntil()).isAfter(avant.plusMinutes(14));
        assertThat(user.getLockedUntil()).isBefore(avant.plusMinutes(16));
    }

    @Test
    void tentativePendantLeVerrouNemetAucunJwtMemeAvecLeBonMotDePasse() throws Exception {
        String email = seedAccount("lockout-3@assrone.ch");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(loginRequest(email, "mauvais-mot-de-passe", "40.0.0.3"));
        }

        mockMvc.perform(loginRequest(email, PASSWORD, "40.0.0.3"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(GENERIC_BODY))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void reponseHttpIdentiqueEntreMauvaisMotDePasseEtCompteVerrouille() throws Exception {
        String email = seedAccount("lockout-4@assrone.ch");
        var mauvaisMotDePasse = mockMvc.perform(loginRequest(email, "mauvais-mot-de-passe", "40.0.0.4"))
                .andReturn();

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(loginRequest(email, "mauvais-mot-de-passe", "40.0.0.4"));
        }
        var compteVerrouille = mockMvc.perform(loginRequest(email, PASSWORD, "40.0.0.4"))
                .andReturn();

        assertThat(mauvaisMotDePasse.getResponse().getStatus()).isEqualTo(compteVerrouille.getResponse().getStatus());
        assertThat(mauvaisMotDePasse.getResponse().getContentAsString())
                .isEqualTo(compteVerrouille.getResponse().getContentAsString());
    }

    @Test
    void succesAvantLeSeuilReinitialiseLeCompteur() throws Exception {
        String email = seedAccount("lockout-5@assrone.ch");
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(loginRequest(email, "mauvais-mot-de-passe", "40.0.0.5"));
        }

        mockMvc.perform(loginRequest(email, PASSWORD, "40.0.0.5"))
                .andExpect(status().isOk());

        User user = userInfoRepository.findByEmail(email).orElseThrow();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void expirationDuVerrouRepartAUnEchecAuProchainEchec() throws Exception {
        String email = seedAccount("lockout-6@assrone.ch");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(loginRequest(email, "mauvais-mot-de-passe", "40.0.0.6"));
        }
        User verrouille = userInfoRepository.findByEmail(email).orElseThrow();
        assertThat(verrouille.getLockedUntil()).isNotNull();

        // Simulate the passage of time by moving lockedUntil into the past
        // directly, rather than controlling the application's real Clock.
        verrouille.setLockedUntil(LocalDateTime.now(Clock.systemDefaultZone()).minusSeconds(1));
        userInfoRepository.saveAndFlush(verrouille);

        mockMvc.perform(loginRequest(email, "mauvais-mot-de-passe", "40.0.0.6"))
                .andExpect(status().isUnauthorized());

        User apresExpiration = userInfoRepository.findByEmail(email).orElseThrow();
        assertThat(apresExpiration.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(apresExpiration.getLockedUntil()).isNull();
    }

    @Test
    void tentativesConcurrentesSurLeMemeCompteDepuisPlusieursIpNePerdentAucunEchecEtNeDepassentPasCinq() throws Exception {
        String email = seedAccount("lockout-7@assrone.ch");
        int attempts = 20;
        CountDownLatch latch = new CountDownLatch(attempts);
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        AtomicInteger unauthorizedCount = new AtomicInteger();
        try {
            List<Callable<Void>> tasks = java.util.stream.IntStream.range(0, attempts)
                    .<Callable<Void>>mapToObj(i -> () -> {
                        latch.countDown();
                        latch.await();
                        var result = mockMvc.perform(loginRequest(email, "mauvais-mot-de-passe", "41.0.0." + (i + 1)))
                                .andReturn();
                        if (result.getResponse().getStatus() == 401) {
                            unauthorizedCount.incrementAndGet();
                        }
                        return null;
                    })
                    .toList();

            List<Future<Void>> futures = pool.invokeAll(tasks);
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(unauthorizedCount.get()).isEqualTo(attempts);

        User user = userInfoRepository.findByEmail(email).orElseThrow();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(user.getLockedUntil()).isNotNull();
    }

    /**
     * Regression guard for the exact failures this test class has already
     * caused twice: (1) a nested @Configuration class hijacking the primary
     * @SpringBootTest context, dropping PasswordConfig/SecurityConfig; (2) the
     * extracted top-level config still living under ASSRONE.backend and being
     * picked up by BackendApplication's component scan anyway, colliding with
     * ClockConfig's "clock" bean. This fails immediately, during context
     * assertions, rather than as a cryptic exception surfacing from every
     * other test method.
     */
    @Test
    void leContextePrincipalExposeLesBeansDeProductionAttendus() {
        assertThat(passwordEncoder).isNotNull();
        assertThat(loginAttemptService).isNotNull();
        assertThat(authenticationManager).isNotNull();

        Map<String, Clock> clockBeans = applicationContext.getBeansOfType(Clock.class);
        assertThat(clockBeans).hasSize(1);
        assertThat(clockBeans).containsOnlyKeys("clock");

        assertThat(applicationContext.containsBeanDefinition("loginAttemptSecondContextTestConfig")).isFalse();
        assertThat(applicationContext.getBeanNamesForType(LoginAttemptSecondContextTestConfig.class)).isEmpty();
    }

    @Test
    void leVerrouEstVisibleDepuisUnSecondContexteApplicatifIndependantCarPersisteEnBase() throws Exception {
        String email = seedAccount("lockout-8@assrone.ch");

        try (AnnotationConfigApplicationContext secondContext = LoginAttemptSecondContextTestConfig.createContext(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {

            LoginAttemptService proxied = secondContext.getBean(LoginAttemptService.class);
            assertThat(AopUtils.isAopProxy(proxied)).isTrue();
            assertThat(proxied).isNotSameAs(loginAttemptService);

            EntityManagerFactory secondEntityManagerFactory = secondContext.getBean(EntityManagerFactory.class);
            assertThat(secondEntityManagerFactory).isNotSameAs(entityManagerFactory);

            DataSource secondDataSource = secondContext.getBean(DataSource.class);
            assertThat(secondDataSource).isNotSameAs(dataSource);
            assertThat(jdbcUrlOf(secondDataSource)).isEqualTo(jdbcUrlOf(dataSource));

            // The second context's own Clock is a singleton within that context,
            // distinct from the primary context's ClockConfig bean — proving the
            // two contexts never share configuration or state.
            Map<String, Clock> secondClockBeans = secondContext.getBeansOfType(Clock.class);
            assertThat(secondClockBeans).hasSize(1);
            assertThat(secondClockBeans.values().iterator().next()).isNotSameAs(clock);

            // Negative control: the raw, unproxied target reproduces the exact
            // failure this whole fix addresses — calling it directly bypasses
            // @Transactional entirely, exactly like the original bug did.
            Object rawTarget = AopProxyUtils.getSingletonTarget(proxied);
            assertThat(rawTarget).isInstanceOf(LoginAttemptService.class).isNotSameAs(proxied);
            LoginAttemptService raw = (LoginAttemptService) rawTarget;
            assertThatThrownBy(() -> raw.registerFailedAttempt(email))
                    .isInstanceOf(InvalidDataAccessApiUsageException.class);

            // Positive control: the proxied bean — a real transaction active for
            // the duration of each call — succeeds every time.
            for (int i = 0; i < 5; i++) {
                proxied.registerFailedAttempt(email);
            }
        }

        mockMvc.perform(loginRequest(email, PASSWORD, "40.0.0.8"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    private static String jdbcUrlOf(DataSource dataSource) {
        return ((HikariDataSource) dataSource).getJdbcUrl();
    }

    @Test
    void requeteBloqueeParLeRateLimitingIpNAtteintJamaisLeCompteurDunAutreCompte() throws Exception {
        String cibleExclue = seedAccount("lockout-9@assrone.ch");
        String ip = "40.0.0.9";

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(loginRequest("nimporte-quoi@assrone.ch", "x", ip));
        }

        mockMvc.perform(loginRequest(cibleExclue, "mauvais-mot-de-passe", ip))
                .andExpect(status().isTooManyRequests());

        User user = userInfoRepository.findByEmail(cibleExclue).orElseThrow();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }
}
