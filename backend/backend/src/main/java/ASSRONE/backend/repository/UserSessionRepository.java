package ASSRONE.backend.repository;

import ASSRONE.backend.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByPublicId(String publicId);

    List<UserSession> findAllByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastUsedAtDesc(
            Long userId, LocalDateTime now);

    long countByUserIdAndRevokedAtIsNullAndExpiresAtAfter(Long userId, LocalDateTime now);

    Optional<UserSession> findFirstByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastUsedAtAsc(
            Long userId, LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserSession s SET s.revokedAt = :revokedAt, s.revocationReason = :reason "
            + "WHERE s.id = :id AND s.revokedAt IS NULL")
    int revokeById(@Param("id") Long id, @Param("revokedAt") LocalDateTime revokedAt, @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserSession s SET s.revokedAt = :revokedAt, s.revocationReason = :reason "
            + "WHERE s.userId = :userId AND s.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") Long userId, @Param("revokedAt") LocalDateTime revokedAt,
                          @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserSession s SET s.revokedAt = :revokedAt, s.revocationReason = :reason "
            + "WHERE s.userId = :userId AND s.id <> :keepId AND s.revokedAt IS NULL")
    int revokeAllForUserExcept(@Param("userId") Long userId, @Param("keepId") Long keepId,
                                @Param("revokedAt") LocalDateTime revokedAt, @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserSession s SET s.lastUsedAt = :lastUsedAt, s.expiresAt = :expiresAt, s.lastSeenIp = :lastSeenIp "
            + "WHERE s.id = :id")
    int touch(@Param("id") Long id, @Param("lastUsedAt") LocalDateTime lastUsedAt,
              @Param("expiresAt") LocalDateTime expiresAt, @Param("lastSeenIp") String lastSeenIp);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM UserSession s WHERE s.revokedAt IS NOT NULL AND s.revokedAt < :before")
    int deleteRevokedBefore(@Param("before") LocalDateTime before);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM UserSession s WHERE s.revokedAt IS NULL AND s.expiresAt < :before")
    int deleteExpiredAndNeverRevokedBefore(@Param("before") LocalDateTime before);
}
