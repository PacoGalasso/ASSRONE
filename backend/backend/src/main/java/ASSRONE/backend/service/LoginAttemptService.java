package ASSRONE.backend.service;

import ASSRONE.backend.repository.UserInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Tracks failed login attempts per account and applies a temporary lockout.
 * Never distinguishes a nonexistent account from a real one in its observable
 * behavior (both simply result in no visible difference to the caller) — the
 * existence check below only decides whether to write, it has no effect on
 * the HTTP response, preserving the login endpoint's anti-enumeration contract.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserInfoRepository repository;
    private final Clock clock;

    @Transactional
    public void registerFailedAttempt(String normalizedEmail) {
        if (normalizedEmail == null || repository.findByEmail(normalizedEmail).isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime lockUntil = now.plus(LOCK_DURATION);
        repository.registerFailedLoginAttempt(normalizedEmail, now, MAX_ATTEMPTS, lockUntil);
    }

    @Transactional
    public void resetFailedAttempts(String normalizedEmail) {
        if (normalizedEmail == null) {
            return;
        }
        repository.resetFailedLoginAttempts(normalizedEmail);
    }

    /**
     * Read-only check used only to decide, after a failed attempt has already
     * been registered, whether that specific attempt was the one that crossed
     * the lockout threshold — so the caller can log a distinct ACCOUNT_LOCKED
     * audit event instead of a plain LOGIN_FAILURE. Never used to gate
     * authentication itself; that remains entirely UserInfoDetails/Spring
     * Security's job.
     */
    @Transactional(readOnly = true)
    public boolean isCurrentlyLocked(String normalizedEmail) {
        if (normalizedEmail == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return repository.findByEmail(normalizedEmail)
                .map(user -> user.getLockedUntil() != null && user.getLockedUntil().isAfter(now))
                .orElse(false);
    }
}
