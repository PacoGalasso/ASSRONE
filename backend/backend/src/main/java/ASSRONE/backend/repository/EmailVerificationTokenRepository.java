package ASSRONE.backend.repository;

import ASSRONE.backend.model.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    // Same concurrency-safe, atomic "claim" pattern as
    // PasswordResetTokenRepository#markUsedIfValid — see there for the full
    // reasoning.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EmailVerificationToken t SET t.usedAt = :usedAt "
            + "WHERE t.id = :id AND t.usedAt IS NULL AND t.expiresAt > :now")
    int markUsedIfValid(@Param("id") Long id, @Param("usedAt") LocalDateTime usedAt, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EmailVerificationToken t SET t.usedAt = :usedAt WHERE t.userId = :userId AND t.usedAt IS NULL")
    int invalidateAllActiveForUser(@Param("userId") Long userId, @Param("usedAt") LocalDateTime usedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM EmailVerificationToken t WHERE t.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") LocalDateTime before);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM EmailVerificationToken t WHERE t.usedAt IS NOT NULL AND t.usedAt < :before")
    int deleteUsedBefore(@Param("before") LocalDateTime before);
}
