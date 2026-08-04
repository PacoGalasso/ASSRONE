package ASSRONE.backend.exception;

public class EventCapacityConflictException extends RuntimeException {
    public EventCapacityConflictException(String message) {
        super(message);
    }
}
