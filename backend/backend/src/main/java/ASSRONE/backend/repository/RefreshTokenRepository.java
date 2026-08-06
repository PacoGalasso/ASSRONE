package ASSRONE.backend.repository;

import ASSRONE.backend.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByJti(String jti);

    /**
     * Atomic per-row UPDATE (same idiom as UserInfoRepository#updateRole):
     * the write is issued and flushed immediately rather than depending on
     * Hibernate's merge/dirty-checking timing. Guarded by "revokedAt IS NULL"
     * so revoking an already-revoked row is a harmless no-op (0 rows
     * affected), never a double-write.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revokedAt = :revokedAt WHERE t.jti = :jti AND t.revokedAt IS NULL")
    int revokeByJti(@Param("jti") String jti, @Param("revokedAt") LocalDateTime revokedAt);

    /**
     * Used for reuse-detection fallout: revoke every still-active token for a
     * user in one statement, so a stolen-and-replayed refresh token can't be
     * used again via a still-valid sibling issued earlier.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revokedAt = :revokedAt WHERE t.userId = :userId AND t.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") Long userId, @Param("revokedAt") LocalDateTime revokedAt);

    /**
     * Used when a single session is revoked: every refresh token minted
     * across that session's rotations (not just its current one) must stop
     * working, so the next presented one — whichever rotation it is — is
     * denied.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revokedAt = :revokedAt WHERE t.sessionId = :sessionId AND t.revokedAt IS NULL")
    int revokeAllForSession(@Param("sessionId") Long sessionId, @Param("revokedAt") LocalDateTime revokedAt);

    /**
     * Used by "revoke other sessions": every token belonging to any session
     * other than the caller's current one is revoked, but the current
     * session's own still-open refresh token is deliberately left alone —
     * otherwise the caller performing this action would be logged out too.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revokedAt = :revokedAt "
            + "WHERE t.userId = :userId AND t.sessionId <> :keepSessionId AND t.revokedAt IS NULL")
    int revokeAllForUserExceptSession(@Param("userId") Long userId, @Param("keepSessionId") Long keepSessionId,
                                       @Param("revokedAt") LocalDateTime revokedAt);
}
