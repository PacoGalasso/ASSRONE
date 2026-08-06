package ASSRONE.backend.exception;

/** A session ID path variable that isn't even a well-formed opaque ID. */
public class InvalidSessionIdException extends RuntimeException {
    public InvalidSessionIdException(String message) {
        super(message);
    }
}
