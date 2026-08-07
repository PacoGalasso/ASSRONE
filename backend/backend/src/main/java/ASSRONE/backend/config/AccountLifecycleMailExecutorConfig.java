package ASSRONE.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * The bounded pool AccountLifecycleEmailListener submits password-reset and
 * email-verification sends to, so the SMTP round trip never blocks the
 * request thread that triggered it (see the listener for why: it runs
 * synchronously, on that same thread, inside the AFTER_COMMIT callback of
 * the triggering @Transactional method — the caller's HTTP response cannot
 * be written until that callback returns).
 *
 * <p>Deliberately small and explicit rather than unbounded or ad hoc:
 * <ul>
 *   <li>core/max pool size: this application sends at most two kinds of
 *   short-lived, low-volume emails through this executor — a handful of
 *   concurrent SMTP round trips is the realistic ceiling, not something
 *   needing a large pool;</li>
 *   <li>bounded queue: caps how many sends can be waiting at once, so a
 *   genuinely stuck SMTP server degrades into rejections (see the
 *   listener's explicit RejectedExecutionException handling — audited,
 *   never silently dropped) instead of unbounded memory growth;</li>
 *   <li>AbortPolicy: the default, kept explicit here rather than left
 *   implicit — a rejection must reach the caller (the listener) so it can
 *   record ACCOUNT_LIFECYCLE_EMAIL_SEND_FAILED, not be silently discarded
 *   or forced onto some other thread;</li>
 *   <li>graceful shutdown: waits (bounded) for in-flight sends to finish
 *   on application shutdown instead of abandoning them mid-send.</li>
 * </ul>
 */
@Configuration
public class AccountLifecycleMailExecutorConfig {

    public static final String BEAN_NAME = "accountLifecycleMailExecutor";

    @Bean(BEAN_NAME)
    public Executor accountLifecycleMailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("account-lifecycle-mail-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
