package ASSRONE.backend.repository;

import ASSRONE.backend.model.EventRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRegistrationRepository extends JpaRepository<EventRegistration, Long> {

    boolean existsByEventIdAndNormalizedEmail(Long eventId, String normalizedEmail);

    void deleteByEventId(Long eventId);
}
