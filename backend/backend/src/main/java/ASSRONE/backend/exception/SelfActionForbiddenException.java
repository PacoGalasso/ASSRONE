package ASSRONE.backend.exception;

public class SelfActionForbiddenException extends RuntimeException {
    public SelfActionForbiddenException(String message) {
        super(message);
    }
}
