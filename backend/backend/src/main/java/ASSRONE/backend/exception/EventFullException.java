package ASSRONE.backend.exception;

public class EventFullException extends RuntimeException {
    public EventFullException(String message) {
        super(message);
    }
}
