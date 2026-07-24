package ASSRONE.backend.dto;

import ASSRONE.backend.model.ApplicationStatus;
import ASSRONE.backend.model.MembershipType;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MembershipApplicationDto {
    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private MembershipType membershipType;
    private String message;
    private ApplicationStatus status;
    private LocalDateTime submittedAt;
}
