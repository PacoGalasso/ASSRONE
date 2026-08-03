package ASSRONE.backend.config;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayMigrationConfig {

    /**
     * By default Spring Boot runs Flyway's migrate() before Hibernate's
     * ddl-auto=update, since JpaBaseConfiguration makes the EntityManagerFactory
     * depend on FlywayMigrationInitializer. Supplying this no-op strategy makes
     * that automatic call a no-op instead; the real migrate() is triggered
     * explicitly by FlywayStartupMigrator once Hibernate has already created the
     * schema, so membership_applications is guaranteed to exist when V1 runs.
     */
    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
        };
    }
}
