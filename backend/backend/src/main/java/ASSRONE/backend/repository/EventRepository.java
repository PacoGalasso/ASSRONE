package ASSRONE.backend.repository;

import ASSRONE.backend.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByEventDateGreaterThanEqualOrderByEventDateAsc(LocalDate date);

    List<Event> findAllByOrderByEventDateAsc();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           UPDATE Event e
           SET e.currentParticipants = e.currentParticipants + 1
           WHERE e.id = :id
             AND e.currentParticipants < e.maxParticipants
           """)
    int incrementParticipantsIfCapacityAvailable(@Param("id") Long id);
}