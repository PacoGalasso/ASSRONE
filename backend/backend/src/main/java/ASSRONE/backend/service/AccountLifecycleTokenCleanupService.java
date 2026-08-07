package ASSRONE.backend.service;

import ASSRONE.backend.audit.SecurityAuditService;
import ASSRONE.backend.audit.SecurityEventResult;
import ASSRONE.backend.audit.SecurityEventType;
import ASSRONE.backend.config.AccountLifecycleProperties;
import ASSRONE.backend.repository.EmailVerificationTokenRepository;
import ASSRONE.backend.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Periodically purges password-reset and email-verification tokens that no
 * longer serve any purpose: expired (whether ever used or not) and used
 * tokens older than the configured retention window. Mirrors
 * SessionCleanupService exactly — see there for the full reasoning
 * (disableable for tests, safe to run redundantly across instances, each
 * repository call a single bounded SQL DELETE that never loads a row into
 * memory).
 *
 * Only aggregate counts are ever logged — never a token, never a hash, never
 * which user a purged token belonged to.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.security.account-lifecycle.cleanup", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class AccountLifecycleTokenCleanupService {

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final AccountLifecycleProperties properties;
    private final SecurityAuditService securityAuditService;
    private final Clock clock;

    public record CleanupResult(int passwordResetTokensDeleted, int emailVerificationTokensDeleted) {
    }

    @Scheduled(cron = "${app.security.account-lifecycle.cleanup.cron:0 30 3 * * *}")
    public void purgeStaleTokens() {
        CleanupResult result = cleanup(properties.cleanup().retentionAfterExpiry());
        if (result.passwordResetTokensDeleted() > 0 || result.emailVerificationTokensDeleted() > 0) {
            log.info("Nettoyage des tokens de cycle de vie du compte : {} tokens de réinitialisation purgés, "
                            + "{} tokens de vérification purgés.",
                    result.passwordResetTokensDeleted(), result.emailVerificationTokensDeleted());
            securityAuditService.record(SecurityEventType.ACCOUNT_LIFECYCLE_TOKEN_CLEANUP_COMPLETED,
                    SecurityEventResult.SUCCESS, "-", null, "accountLifecycleTokens", null,
                    "PASSWORD_RESET=" + result.passwordResetTokensDeleted()
                            + ";EMAIL_VERIFICATION=" + result.emailVerificationTokensDeleted());
        }
    }

    @Transactional
    public CleanupResult cleanup(java.time.Duration retentionAfterExpiry) {
        // One retention window applied uniformly, mirroring SessionService#cleanup:
        // a token — whether it was used or simply expired unused — is kept this
        // long afterward before being purged.
        LocalDateTime before = LocalDateTime.now(clock).minus(retentionAfterExpiry);

        int passwordResetDeleted = passwordResetTokenRepository.deleteUsedBefore(before)
                + passwordResetTokenRepository.deleteExpiredBefore(before);
        int emailVerificationDeleted = emailVerificationTokenRepository.deleteUsedBefore(before)
                + emailVerificationTokenRepository.deleteExpiredBefore(before);

        return new CleanupResult(passwordResetDeleted, emailVerificationDeleted);
    }
}
