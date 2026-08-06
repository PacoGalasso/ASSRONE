package ASSRONE.backend.exception;

/**
 * Covers every way a presented email-verification token can fail — unknown,
 * expired, or already used — behind one generic message, mirroring
 * InvalidPasswordResetTokenException's reasoning exactly.
 */
public class InvalidEmailVerificationTokenException extends RuntimeException {
    public InvalidEmailVerificationTokenException(String message) {
        super(message);
    }
}
