package ASSRONE.backend.exception;

/**
 * Covers every way a presented password-reset token can fail — unknown,
 * expired, or already used — behind one generic message. Distinguishing
 * these to the client would let an attacker probe which case applies to a
 * given token; the real reason is still recorded internally via the audit
 * event's reasonCode (see PasswordResetService).
 */
public class InvalidPasswordResetTokenException extends RuntimeException {
    public InvalidPasswordResetTokenException(String message) {
        super(message);
    }
}
