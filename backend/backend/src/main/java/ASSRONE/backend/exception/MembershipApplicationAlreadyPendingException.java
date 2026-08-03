package ASSRONE.backend.exception;

public class MembershipApplicationAlreadyPendingException extends RuntimeException {
    public MembershipApplicationAlreadyPendingException(String message) {
        super(message);
    }
}
