package ASSRONE.backend.exception;

public class EventRegistrationAlreadyExistsException extends RuntimeException {
    public EventRegistrationAlreadyExistsException(String message) {
        super(message);
    }
}
