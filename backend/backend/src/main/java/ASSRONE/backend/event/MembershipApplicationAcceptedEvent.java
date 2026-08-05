package ASSRONE.backend.event;

public record MembershipApplicationAcceptedEvent(
        Long applicationId,
        String fullName,
        String email,
        String rawPassword
) {
}
