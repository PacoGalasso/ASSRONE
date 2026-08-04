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
}
