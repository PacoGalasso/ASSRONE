package ASSRONE.backend.repository;

import ASSRONE.backend.model.CommitteeMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommitteeMemberRepository extends JpaRepository<CommitteeMember, Long> {
    List<CommitteeMember> findByActiveTrueOrderByDisplayOrderAscLastNameAsc();

    List<CommitteeMember> findAllByOrderByDisplayOrderAscLastNameAsc();
}
