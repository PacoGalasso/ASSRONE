package ASSRONE.backend.exception;

/**
 * Thrown from the login flow only after credentials have already matched —
 * the caller already knows the account exists and the password is correct,
 * so naming the real reason here (unlike a bad password) does not create a
 * new account-enumeration channel.
 */
public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException(String message) {
        super(message);
    }
}
