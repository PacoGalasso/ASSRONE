package ASSRONE.backend.event;

/**
 * Published on new-account registration and on a resend request. Same
 * in-memory-only, never-logged handling as PasswordResetRequestedEvent.
 */
public record EmailVerificationRequestedEvent(String email, String rawToken) {
}
