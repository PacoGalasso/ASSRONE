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

    /**
     * Same atomicity idiom as incrementParticipantsIfCapacityAvailable: a single
     * conditional UPDATE closes the race between an admin lowering maxParticipants
     * and a concurrent registration incrementing currentParticipants — whichever
     * statement's row lock is acquired first on PostgreSQL wins, the other sees
     * the already-updated value. 0 rows affected means the requested capacity is
     * below the current registration count as of that atomic check.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           UPDATE Event e
           SET e.maxParticipants = :maxParticipants
           WHERE e.id = :id
             AND e.currentParticipants <= :maxParticipants
           """)
    int updateMaxParticipantsIfSufficientCapacity(@Param("id") Long id, @Param("maxParticipants") Integer maxParticipants);
}