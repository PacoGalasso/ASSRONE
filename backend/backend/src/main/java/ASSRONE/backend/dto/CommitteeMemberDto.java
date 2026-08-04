package ASSRONE.backend.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommitteeMemberDto {
    private Long id;
    private String firstName;
    private String lastName;
    private String role;
    private String description;
    private boolean hasPhoto;
    private Integer displayOrder;
    private boolean active;
}
