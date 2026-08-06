package ASSRONE.backend.service;

import ASSRONE.backend.audit.SecurityAuditService;
import ASSRONE.backend.audit.SecurityEventResult;
import ASSRONE.backend.audit.SecurityEventType;
import ASSRONE.backend.config.AccountLifecycleProperties;
import ASSRONE.backend.event.EmailVerificationRequestedEvent;
import ASSRONE.backend.exception.InvalidEmailVerificationTokenException;
import ASSRONE.backend.model.EmailVerificationToken;
import ASSRONE.backend.model.User;
import ASSRONE.backend.repository.EmailVerificationTokenRepository;
import ASSRONE.backend.repository.UserInfoRepository;
import ASSRONE.backend.security.EmailNormalizer;
import ASSRONE.backend.security.SecureTokenGenerator;
import ASSRONE.backend.security.TokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Owns email verification: issuing a token at registration, verifying it,
 * and resending it. Like PasswordResetService, resend() always returns the
 * same generic outcome regardless of whether the email corresponds to a
 * real (or already-verified) account.
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final String GENERIC_VERIFICATION_FAILURE_MESSAGE = "Ce lien de vérification n'est plus valide.";

    private final UserInfoRepository userInfoRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final AccountLifecycleProperties properties;
    private final SecurityAuditService securityAuditService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * Called right after a new account is persisted (see
     * UserInfoService#addUser). Its own transaction, deliberately separate
     * from the user-creation one it follows: a failure here never rolls back
     * an otherwise-successful registration, and the user can always recover
     * via resend().
     */
    @Transactional
    public void issueTokenAndSendEmail(User user) {
        issueTokenAndSendEmail(user, "REGISTRATION");
    }

    /**
     * Same as {@link #issueTokenAndSendEmail(User)}, but for a caller that
     * needs the audit trail to reflect a different trigger — e.g. a profile
     * email change (see UserProfileService#updateProfile) rather than a
     * brand-new registration.
     */
    @Transactional
    public void issueTokenAndSendEmail(User user, String reasonCode) {
        issueAndPublish(user);
        securityAuditService.record(SecurityEventType.EMAIL_VERIFICATION_REQUESTED, SecurityEventResult.SUCCESS,
                String.valueOf(user.getId()), null, "user", String.valueOf(user.getId()), reasonCode);
    }

    /**
     * Always succeeds from the caller's point of view — see requestReset in
     * PasswordResetService for the identical anti-enumeration reasoning.
     * Also a no-op (but still the same generic outcome) for an already-
     * verified account, so resending doesn't leak verification status
     * either.
     */
    @Transactional
    public void resend(String rawEmail) {
        String normalizedEmail = EmailNormalizer.normalize(rawEmail);
        Optional<User> user = userInfoRepository.findByEmail(normalizedEmail);

        if (user.isEmpty()) {
            securityAuditService.record(SecurityEventType.EMAIL_VERIFICATION_RESENT, SecurityEventResult.SUCCESS,
                    "-", null, "user", null, "ACCOUNT_NOT_FOUND");
            return;
        }

        User account = user.get();
        if (account.getEmailVerifiedAt() != null) {
            securityAuditService.record(SecurityEventType.EMAIL_VERIFICATION_RESENT, SecurityEventResult.SUCCESS,
                    String.valueOf(account.getId()), null, "user", String.valueOf(account.getId()), "ALREADY_VERIFIED");
            return;
        }

        issueAndPublish(account);
        securityAuditService.record(SecurityEventType.EMAIL_VERIFICATION_RESENT, SecurityEventResult.SUCCESS,
                String.valueOf(account.getId()), null, "user", String.valueOf(account.getId()), "ACCOUNT_FOUND");
    }

    /**
     * Idempotent by design: verifying an already-used-but-still-matching
     * link a second time would fail via markUsedIfValid like any other
     * reused token — callers see the same generic failure, not a special
     * "already verified" success, so this never confirms account existence
     * to someone who merely has an old, expired link.
     */
    @Transactional
    public void verify(String rawToken) {
        String tokenHash = TokenHasher.sha256Hex(rawToken);
        EmailVerificationToken token = emailVerificationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> denyVerification("-", "TOKEN_NOT_FOUND"));

        LocalDateTime now = LocalDateTime.now(clock);
        int claimed = emailVerificationTokenRepository.markUsedIfValid(token.getId(), now, now);
        if (claimed == 0) {
            String reason = token.getUsedAt() != null ? "ALREADY_USED" : "EXPIRED";
            throw denyVerification(String.valueOf(token.getUserId()), reason);
        }

        User user = userInfoRepository.findById(token.getUserId())
                .orElseThrow(() -> denyVerification(String.valueOf(token.getUserId()), "USER_NOT_FOUND"));

        user.setEmailVerifiedAt(now);
        userInfoRepository.save(user);
        emailVerificationTokenRepository.invalidateAllActiveForUser(user.getId(), now);

        securityAuditService.record(SecurityEventType.EMAIL_VERIFIED, SecurityEventResult.SUCCESS,
                String.valueOf(user.getId()), null, "user", String.valueOf(user.getId()), null);
    }

    private void issueAndPublish(User user) {
        LocalDateTime now = LocalDateTime.now(clock);
        emailVerificationTokenRepository.invalidateAllActiveForUser(user.getId(), now);

        String rawToken = SecureTokenGenerator.generate();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .userId(user.getId())
                .tokenHash(TokenHasher.sha256Hex(rawToken))
                .createdAt(now)
                .expiresAt(now.plus(properties.verificationToken().expiry()))
                .build();
        emailVerificationTokenRepository.save(token);

        eventPublisher.publishEvent(new EmailVerificationRequestedEvent(user.getEmail(), rawToken));
    }

    private InvalidEmailVerificationTokenException denyVerification(String actorId, String reasonCode) {
        securityAuditService.record(SecurityEventType.EMAIL_VERIFICATION_FAILED, SecurityEventResult.DENIED,
                actorId, null, "emailVerificationToken", null, reasonCode);
        return new InvalidEmailVerificationTokenException(GENERIC_VERIFICATION_FAILURE_MESSAGE);
    }
}
