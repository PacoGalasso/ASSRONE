package ASSRONE.backend.service;

import ASSRONE.backend.audit.SecurityAuditService;
import ASSRONE.backend.audit.SecurityEventResult;
import ASSRONE.backend.audit.SecurityEventType;
import ASSRONE.backend.config.AccountLifecycleProperties;
import ASSRONE.backend.event.PasswordResetRequestedEvent;
import ASSRONE.backend.exception.InvalidPasswordResetTokenException;
import ASSRONE.backend.model.PasswordResetToken;
import ASSRONE.backend.model.User;
import ASSRONE.backend.repository.PasswordResetTokenRepository;
import ASSRONE.backend.repository.UserInfoRepository;
import ASSRONE.backend.security.EmailNormalizer;
import ASSRONE.backend.security.PasswordPolicy;
import ASSRONE.backend.security.SecureTokenGenerator;
import ASSRONE.backend.security.TokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Owns the "forgot password" / "reset password" flow end to end. Every
 * public method always returns the same generic outcome regardless of
 * whether the email corresponds to a real account — see requestReset — so
 * this class is also the one place account enumeration through this flow
 * would be introduced by accident; treat any change here that lets the two
 * paths (found/not found) diverge as a regression.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final String GENERIC_RESET_FAILURE_MESSAGE = "Ce lien de réinitialisation n'est plus valide.";

    private final UserInfoRepository userInfoRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AccountLifecycleProperties properties;
    private final SecurityAuditService securityAuditService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * Always succeeds from the caller's point of view: the generic response
     * is the controller's job to return unconditionally, this method just
     * does (or doesn't, if there's no such account) the real work behind it.
     * Rate limiting (IP and per-identity) is enforced by the caller — see
     * PasswordResetController — before this is ever invoked.
     */
    @Transactional
    public void requestReset(String rawEmail) {
        String normalizedEmail = EmailNormalizer.normalize(rawEmail);
        Optional<User> user = userInfoRepository.findByEmail(normalizedEmail);

        if (user.isEmpty()) {
            // No token, no email, no database write — but still one audit
            // line, so operators can see request volume against unknown
            // addresses without that information ever reaching the client.
            securityAuditService.record(SecurityEventType.PASSWORD_RESET_REQUESTED, SecurityEventResult.SUCCESS,
                    "-", null, "user", null, "ACCOUNT_NOT_FOUND");
            return;
        }

        User account = user.get();
        LocalDateTime now = LocalDateTime.now(clock);
        passwordResetTokenRepository.invalidateAllActiveForUser(account.getId(), now);

        String rawToken = SecureTokenGenerator.generate();
        PasswordResetToken token = PasswordResetToken.builder()
                .userId(account.getId())
                .tokenHash(TokenHasher.sha256Hex(rawToken))
                .createdAt(now)
                .expiresAt(now.plus(properties.resetToken().expiry()))
                .build();
        passwordResetTokenRepository.save(token);

        securityAuditService.record(SecurityEventType.PASSWORD_RESET_REQUESTED, SecurityEventResult.SUCCESS,
                String.valueOf(account.getId()), null, "user", String.valueOf(account.getId()), "ACCOUNT_FOUND");

        // Deferred to AFTER_COMMIT (see AccountLifecycleEmailListener) so the
        // email only ever goes out once the token row is durably persisted.
        eventPublisher.publishEvent(new PasswordResetRequestedEvent(account.getEmail(), rawToken));
    }

    /**
     * Atomic: verifies the token, sets the new password, invalidates every
     * other outstanding reset token for the user, and revokes every session
     * and refresh token — all within this one transaction. Two concurrent
     * calls presenting the same token can never both succeed (see
     * PasswordResetTokenRepository#markUsedIfValid).
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        String tokenHash = TokenHasher.sha256Hex(rawToken);
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> denyReset("-", "TOKEN_NOT_FOUND"));

        LocalDateTime now = LocalDateTime.now(clock);
        int claimed = passwordResetTokenRepository.markUsedIfValid(token.getId(), now, now);
        if (claimed == 0) {
            // Best-effort reason code for the audit log only: in the narrow
            // race where a concurrent request claims the token between this
            // method's initial fetch and this UPDATE, usedAt here still
            // reflects the pre-race read and this can report "EXPIRED" where
            // "ALREADY_USED" would be more precise. The security guarantee
            // itself is unaffected either way — markUsedIfValid is what
            // actually decides, atomically, that this call loses the race.
            String reason = token.getUsedAt() != null ? "ALREADY_USED" : "EXPIRED";
            throw denyReset(String.valueOf(token.getUserId()), reason);
        }

        User user = userInfoRepository.findById(token.getUserId())
                .orElseThrow(() -> denyReset(String.valueOf(token.getUserId()), "USER_NOT_FOUND"));

        PasswordPolicy.validate(newPassword);

        user.setPassword(passwordEncoder.encode(newPassword));
        userInfoRepository.save(user);

        passwordResetTokenRepository.invalidateAllActiveForUser(user.getId(), now);
        refreshTokenService.revokeAllForUser(user.getId());

        securityAuditService.record(SecurityEventType.PASSWORD_RESET_SUCCEEDED, SecurityEventResult.SUCCESS,
                String.valueOf(user.getId()), null, "user", String.valueOf(user.getId()), null);
    }

    private InvalidPasswordResetTokenException denyReset(String actorId, String reasonCode) {
        securityAuditService.record(SecurityEventType.PASSWORD_RESET_FAILED, SecurityEventResult.DENIED,
                actorId, null, "passwordResetToken", null, reasonCode);
        return new InvalidPasswordResetTokenException(GENERIC_RESET_FAILURE_MESSAGE);
    }
}
