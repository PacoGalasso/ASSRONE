package ASSRONE.backend.config;

import lombok.RequiredArgsConstructor;
import org.flywaydb.core.Flyway;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Runs the real Flyway migration once the application is fully started, i.e.
 * after Hibernate's ddl-auto=update has already created/updated the schema.
 * See FlywayMigrationConfig for why the automatic pre-Hibernate call is disabled.
 */
@Component
@RequiredArgsConstructor
public class FlywayStartupMigrator {

    private final Flyway flyway;

    @EventListener(ApplicationReadyEvent.class)
    public void migrate() {
        flyway.migrate();
    }
}
