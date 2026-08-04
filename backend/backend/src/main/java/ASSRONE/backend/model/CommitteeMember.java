package ASSRONE.backend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "committee_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommitteeMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String firstName;

    @Column(nullable = false, length = 80)
    private String lastName;

    @Column(nullable = false, length = 100)
    private String role;

    @Column(length = 1200)
    private String description;

    private String photoFilename;

    @Column(nullable = false)
    private Integer displayOrder;

    @Column(nullable = false)
    private boolean active;
}
