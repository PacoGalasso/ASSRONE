package ASSRONE.backend.service;

import ASSRONE.backend.config.SessionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically purges sessions that no longer serve any purpose: revoked
 * sessions older than the configured retention window (kept that long so
 * RefreshTokenService#rotate can still tell a legitimate revocation apart
 * from token reuse — see the SESSION_REVOKED check there), and sessions
 * that simply expired without ever being explicitly revoked.
 *
 * Disableable via app.security.sessions.cleanup.enabled (defaults on) so
 * the test suite — and any environment that doesn't want a background
 * scheduler — never has it fire unpredictably. The cron expression itself
 * is read directly from the same property the validated SessionProperties
 * record also binds, so both always agree; Spring's @Scheduled only
 * accepts a property placeholder here, not the typed record.
 *
 * Safe to run redundantly if more than one application instance is ever
 * deployed: every underlying repository call is a plain SQL DELETE guarded
 * by its own WHERE clause, so two overlapping runs just both delete the
 * same (already-gone, for the second one) rows — no distributed lock is
 * needed for correctness, only for avoiding wasted work, which isn't worth
 * the added complexity at this application's scale.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.security.sessions.cleanup", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SessionCleanupService {

    private final SessionService sessionService;
    private final SessionProperties sessionProperties;

    @Scheduled(cron = "${app.security.sessions.cleanup.cron:0 0 3 * * *}")
    public void purgeStaleSessions() {
        SessionService.CleanupResult result = sessionService.cleanup(sessionProperties.cleanup().retentionAfterExpiry());
        if (result.revokedSessionsDeleted() > 0 || result.expiredSessionsDeleted() > 0) {
            log.info("Nettoyage des sessions : {} révoquées purgées, {} expirées purgées.",
                    result.revokedSessionsDeleted(), result.expiredSessionsDeleted());
        }
    }
}
