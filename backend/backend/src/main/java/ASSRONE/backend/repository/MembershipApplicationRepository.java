package ASSRONE.backend.repository;


import ASSRONE.backend.model.ApplicationStatus;
import ASSRONE.backend.model.MembershipApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MembershipApplicationRepository extends JpaRepository<MembershipApplication, Long> {
    List<MembershipApplication> findAllByOrderBySubmittedAtDesc();

    boolean existsByEmailAndStatus(String email, ApplicationStatus status);
}
