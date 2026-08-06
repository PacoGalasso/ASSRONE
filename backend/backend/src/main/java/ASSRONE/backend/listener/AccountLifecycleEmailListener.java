package ASSRONE.backend.listener;

import ASSRONE.backend.audit.SecurityAuditService;
import ASSRONE.backend.audit.SecurityEventResult;
import ASSRONE.backend.audit.SecurityEventType;
import ASSRONE.backend.config.AccountLifecycleMailExecutorConfig;
import ASSRONE.backend.event.EmailVerificationRequestedEvent;
import ASSRONE.backend.event.PasswordResetRequestedEvent;
import ASSRONE.backend.filter.CorrelationIdFilter;
import ASSRONE.backend.service.AccountLifecycleEmailService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Mirrors EventRegistrationEmailListener's AFTER_COMMIT timing exactly: runs
 * only once the triggering transaction commits, so a token that ends up not
 * persisted (transaction rolled back) never gets emailed. Unlike that
 * listener, the actual send is dispatched to {@link AccountLifecycleMailExecutorConfig
 * a bounded executor} rather than run inline.
 *
 * <p><b>Why dispatch matters here specifically</b>: {@code @TransactionalEventListener}
 * fires synchronously, on the same thread that just committed the
 * transaction — which, for a request-scoped {@code @Transactional} service
 * method, is the original HTTP request thread. Left inline, this method's
 * own SMTP round trip would run to completion (or timeout) BEFORE
 * PasswordResetController/EmailVerificationController's already-decided,
 * generic response is actually written to the client — the exact "Envoi en
 * cours..." latency this was built to eliminate. The response body is
 * unaffected either way (anti-enumeration was already intact — the
 * controller never inspects this listener's outcome), but the client
 * shouldn't have to wait for a mail server to respond just to see a message
 * that never depended on it in the first place.
 *
 * <p><b>Why a manually-submitted Executor instead of {@code @Async}</b>:
 * combining {@code @Async} with {@code @TransactionalEventListener} works,
 * but a rejected submission (queue full) then throws back through Spring's
 * own event-multicasting machinery — still synchronously, on the same
 * commit-time call stack this class exists to get off of, defeating the
 * point under exactly the overload scenario a bounded queue exists to
 * survive. Submitting to the injected executor directly, inside a plain
 * try/catch, keeps every outcome — success, a real send failure, or a
 * rejected submission — deterministic, local to this class, and audited the
 * same way, regardless of how the pool is doing.
 *
 * <p>No retry: a failure here is either transient (the user's own
 * forgot-password/resend-verification request is already the retry path —
 * asking again invalidates the old token and issues a fresh one) or a real
 * SMTP misconfiguration (a retry loop would not fix that; only fixing the
 * configuration does, which the ACCOUNT_LIFECYCLE_EMAIL_SEND_FAILED audit
 * event below and the ERROR-level log exist to surface quickly in both
 * local dev and production, in place of a bounded retry that would just
 * delay the same operator-visible signal).
 */
@Slf4j
@Component
public class AccountLifecycleEmailListener {

    private final AccountLifecycleEmailService accountLifecycleEmailService;
    private final SecurityAuditService securityAuditService;
    private final Executor mailExecutor;

    public AccountLifecycleEmailListener(
            AccountLifecycleEmailService accountLifecycleEmailService,
            SecurityAuditService securityAuditService,
            @Qualifier(AccountLifecycleMailExecutorConfig.BEAN_NAME) Executor mailExecutor) {
        this.accountLifecycleEmailService = accountLifecycleEmailService;
        this.securityAuditService = securityAuditService;
        this.mailExecutor = mailExecutor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        dispatch(
                () -> accountLifecycleEmailService.sendPasswordResetEmail(event.email(), event.rawToken()),
                "PASSWORD_RESET",
                "Échec de l'envoi de l'email de réinitialisation de mot de passe.");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmailVerificationRequested(EmailVerificationRequestedEvent event) {
        dispatch(
                () -> accountLifecycleEmailService.sendVerificationEmail(event.email(), event.rawToken()),
                "EMAIL_VERIFICATION",
                "Échec de l'envoi de l'email de vérification d'adresse.");
    }

    private void dispatch(Runnable send, String reasonCode, String failureLogMessage) {
        // MDC is thread-local: without capturing it here, on the request
        // thread, and re-applying it on the pool thread below, a failure
        // audited from inside the async task would lose its correlationId —
        // the pool thread has never seen this request's MDC context at all.
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        try {
            mailExecutor.execute(() -> {
                try {
                    MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
                    send.run();
                } catch (RuntimeException ex) {
                    log.error(failureLogMessage, ex);
                    recordSendFailure(reasonCode);
                } finally {
                    MDC.remove(CorrelationIdFilter.MDC_KEY);
                }
            });
        } catch (RejectedExecutionException ex) {
            log.error("File d'attente d'envoi d'emails saturée — {}", failureLogMessage, ex);
            recordSendFailure(reasonCode + "_QUEUE_SATURATED");
        }
    }

    // Never includes the recipient email, the token, or the URL — only which
    // of the two mail kinds failed. Which account triggered it is already
    // recoverable, if genuinely needed, by correlating this line's
    // correlationId with the PASSWORD_RESET_REQUESTED/EMAIL_VERIFICATION_REQUESTED
    // audit line SecurityAuditService already recorded earlier in the same request.
    private void recordSendFailure(String reasonCode) {
        securityAuditService.record(SecurityEventType.ACCOUNT_LIFECYCLE_EMAIL_SEND_FAILED, SecurityEventResult.ERROR,
                "-", null, "accountLifecycleEmail", null, reasonCode);
    }
}
