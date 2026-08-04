package ASSRONE.backend.exception;

public class UserDeletionConflictException extends RuntimeException {
    public UserDeletionConflictException(String message) {
        super(message);
    }
}
