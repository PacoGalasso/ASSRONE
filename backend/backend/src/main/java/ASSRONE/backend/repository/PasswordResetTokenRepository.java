package ASSRONE.backend.repository;

import ASSRONE.backend.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    long countByUserIdAndUsedAtIsNull(Long userId);

    // The concurrency-safety guarantee: this UPDATE only ever matches a row
    // that is still unused and unexpired at the moment Postgres evaluates the
    // WHERE clause under the row lock it takes for the UPDATE itself. Two
    // concurrent reset attempts presenting the same token race for that lock;
    // whichever commits first flips usedAt, and the second's UPDATE then
    // matches zero rows once it acquires the lock and re-evaluates — it can
    // never also succeed. A return value of 1 means "this call won the race
    // and reset can proceed"; 0 means "already used, expired, or unknown".
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :usedAt "
            + "WHERE t.id = :id AND t.usedAt IS NULL AND t.expiresAt > :now")
    int markUsedIfValid(@Param("id") Long id, @Param("usedAt") LocalDateTime usedAt, @Param("now") LocalDateTime now);

    // A fresh request invalidates every still-active token this user already
    // had outstanding (see PasswordResetService#requestReset) — at most one
    // valid reset link is ever live per account, closing off mass-email abuse
    // of repeated requests.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :usedAt WHERE t.userId = :userId AND t.usedAt IS NULL")
    int invalidateAllActiveForUser(@Param("userId") Long userId, @Param("usedAt") LocalDateTime usedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") LocalDateTime before);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PasswordResetToken t WHERE t.usedAt IS NOT NULL AND t.usedAt < :before")
    int deleteUsedBefore(@Param("before") LocalDateTime before);
}
