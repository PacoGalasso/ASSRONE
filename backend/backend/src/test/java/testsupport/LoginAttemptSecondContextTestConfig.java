package testsupport;

import ASSRONE.backend.repository.UserInfoRepository;
import ASSRONE.backend.service.LoginAttemptService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.util.Map;
import java.util.Properties;

/**
 * Configuration for a genuinely separate, independent Spring context, used
 * only by LoginLockoutIntegrationTest's multi-instance test.
 *
 * Package "testsupport" is deliberately NOT under ASSRONE.backend: BackendApplication's
 * @SpringBootApplication performs an implicit @ComponentScan rooted at its own
 * package (ASSRONE.backend) and everything beneath it, which includes test
 * sources on the test classpath. A previous version of this class lived at
 * ASSRONE.backend.controller and — despite being a plain top-level class, not
 * nested in any test — was still picked up by that scan and loaded into the
 * PRIMARY @SpringBootTest context, colliding with ClockConfig's "clock" bean
 * (BeanDefinitionOverrideException). Being outside ASSRONE.backend entirely is
 * what actually guarantees this config is invisible to that scan, regardless
 * of nesting or annotation choice.
 *
 * Connection details are passed in explicitly via createContext(...) rather
 * than reading a static field on the test class, so this class carries no
 * dependency on any particular test's structure.
 */
@Configuration
@EnableJpaRepositories(basePackageClasses = UserInfoRepository.class)
@EnableTransactionManagement
public class LoginAttemptSecondContextTestConfig {

    @Bean
    DataSource dataSource(
            @Value("${test.second-context.datasource.url}") String url,
            @Value("${test.second-context.datasource.username}") String username,
            @Value("${test.second-context.datasource.password}") String password) {
        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .driverClassName("org.postgresql.Driver")
                .build();
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(dataSource);
        emf.setPackagesToScan("ASSRONE.backend.model");
        emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        // Schema already owned and created by the primary @SpringBootTest
        // context started earlier for this test class; this second context
        // only reads/writes existing tables, it never manages the schema.
        Properties jpaProperties = new Properties();
        jpaProperties.setProperty("hibernate.hbm2ddl.auto", "none");
        emf.setJpaProperties(jpaProperties);
        return emf;
    }

    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    LoginAttemptService loginAttemptService(UserInfoRepository repository, Clock clock) {
        return new LoginAttemptService(repository, clock);
    }

    public static AnnotationConfigApplicationContext createContext(String jdbcUrl, String username, String password) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "second-context-datasource",
                Map.of(
                        "test.second-context.datasource.url", jdbcUrl,
                        "test.second-context.datasource.username", username,
                        "test.second-context.datasource.password", password
                )));
        context.register(LoginAttemptSecondContextTestConfig.class);
        context.refresh();
        return context;
    }
}
