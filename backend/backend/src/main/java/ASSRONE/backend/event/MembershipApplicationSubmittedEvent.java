package ASSRONE.backend.event;

import ASSRONE.backend.model.MembershipType;

public record MembershipApplicationSubmittedEvent(
        Long applicationId,
        String fullName,
        String email,
        String phone,
        MembershipType membershipType,
        String message
) {
}
