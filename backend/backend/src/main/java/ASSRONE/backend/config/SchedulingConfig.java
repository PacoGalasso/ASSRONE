package ASSRONE.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} methods application-wide (currently only
 * SessionCleanupService). Kept as its own tiny class, mirroring
 * ClockConfig, rather than annotating BackendApplication directly.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
