package ASSRONE.backend.repository;

import ASSRONE.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserInfoRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email); // Use 'email' if that is the correct field for login

    long countByRole(String role);

    /**
     * Atomic per-row UPDATE for admin-driven role changes — bypasses the
     * load/mutate/save entity-merge path entirely so the write is unambiguous
     * and immediately flushed, matching the idiom below (registerFailedLoginAttempt)
     * already proven correct under concurrent PostgreSQL access in this codebase.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.role = :role WHERE u.id = :id")
    int updateRole(@Param("id") Long id, @Param("role") String role);

    /**
     * Atomic per-row UPDATE: PostgreSQL holds the row lock for the statement's
     * duration under READ_COMMITTED, so concurrent calls for the same email are
     * serialized and no increment is lost — no read-modify-write from the app,
     * no application-level locking, safe across multiple instances since the
     * source of truth is the database row itself, not in-memory state.
     *
     * The WHERE clause is what prevents incrementing a still-locked account
     * (0 rows affected in that case, existing lock preserved untouched). A lock
     * found already expired is treated as a fresh cycle: this single failure
     * becomes attempt #1 and any previous lockedUntil is cleared, rather than
     * extending the old count.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           UPDATE User u
           SET u.failedLoginAttempts = CASE
                   WHEN u.lockedUntil IS NOT NULL AND u.lockedUntil < :now THEN 1
                   ELSE u.failedLoginAttempts + 1
               END,
               u.lockedUntil = CASE
                   WHEN u.lockedUntil IS NOT NULL AND u.lockedUntil < :now THEN NULL
                   WHEN u.failedLoginAttempts + 1 >= :threshold THEN :lockUntil
                   ELSE u.lockedUntil
               END
           WHERE u.email = :email
             AND (u.lockedUntil IS NULL OR u.lockedUntil < :now)
           """)
    int registerFailedLoginAttempt(@Param("email") String email, @Param("now") LocalDateTime now,
                                    @Param("threshold") int threshold, @Param("lockUntil") LocalDateTime lockUntil);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           UPDATE User u
           SET u.failedLoginAttempts = 0,
               u.lockedUntil = NULL
           WHERE u.email = :email
           """)
    int resetFailedLoginAttempts(@Param("email") String email);
}
