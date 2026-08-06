package ASSRONE.backend.service;

import ASSRONE.backend.audit.SecurityAuditService;
import ASSRONE.backend.audit.SecurityEventResult;
import ASSRONE.backend.audit.SecurityEventType;
import ASSRONE.backend.exception.LastAdministratorException;
import ASSRONE.backend.exception.SelfActionForbiddenException;
import ASSRONE.backend.exception.UserDeletionConflictException;
import ASSRONE.backend.exception.UserNotFoundException;
import ASSRONE.backend.model.User;
import ASSRONE.backend.model.UserRole;
import ASSRONE.backend.repository.UserInfoRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-only role changes and account deletion, both guarded against removing the
 * last remaining administrator.
 *
 * A plain "COUNT(*) admins WHERE id <> :target" check has a genuine race under
 * READ_COMMITTED: two concurrent transactions demoting/deleting the last two admins
 * can each read "2 admins remain" before either commits, and both proceed, leaving
 * zero. To close that window, any operation that could reduce the admin count first
 * takes a fixed-key Postgres advisory transaction lock (held until commit/rollback),
 * serializing all such operations against each other — the second transaction blocks
 * until the first commits, then re-reads the now-updated count.
 */
@Service
@RequiredArgsConstructor
public class AdminUserManagementService {

    private static final long ADMIN_GUARD_LOCK_KEY = 8_100_001L;
    private static final String ADMIN_ROLE = UserRole.ADMIN.name();

    private final UserInfoRepository repository;
    private final EntityManager entityManager;
    private final SecurityAuditService securityAuditService;

    @Transactional
    public void changeRole(String actorEmail, Long targetId, UserRole newRole) {
        User target = loadTarget(actorEmail, targetId, "ROLE_CHANGE");
        rejectSelfAction(actorEmail, target, "ROLE_CHANGE", "Un administrateur ne peut pas modifier son propre rôle.");

        if (ADMIN_ROLE.equals(target.getRole()) && newRole != UserRole.ADMIN) {
            acquireAdminGuardLock();
            if (repository.countByRole(ADMIN_ROLE) <= 1) {
                recordDenied(actorEmail, targetId, "ROLE_CHANGE", "LAST_ADMINISTRATOR_PROTECTED");
                throw new LastAdministratorException("Impossible de rétrograder le dernier administrateur.");
            }
        }

        // Atomic UPDATE rather than load/mutate/save: the write is issued and
        // flushed immediately instead of depending on Hibernate's merge/dirty-
        // checking flush timing, matching the idiom already proven correct under
        // concurrent PostgreSQL access elsewhere in this codebase (see
        // UserInfoRepository#registerFailedLoginAttempt).
        int updated = repository.updateRole(targetId, newRole.name());
        if (updated != 1) {
            // The target existed moments ago (loadTarget) but the UPDATE affected no
            // row — it was deleted by another operation in between. Never treat this
            // as a silent success.
            recordDenied(actorEmail, targetId, "ROLE_CHANGE", "TARGET_NOT_FOUND");
            throw new UserNotFoundException("Utilisateur introuvable : " + targetId);
        }

        securityAuditService.record(SecurityEventType.ROLE_CHANGE, SecurityEventResult.SUCCESS,
                SecurityAuditService.maskEmail(actorEmail), null, "user", String.valueOf(targetId), newRole.name());
    }

    @Transactional
    public void deleteUser(String actorEmail, Long targetId) {
        User target = loadTarget(actorEmail, targetId, "USER_DELETED");
        rejectSelfAction(actorEmail, target, "USER_DELETED", "Un administrateur ne peut pas supprimer son propre compte.");

        if (ADMIN_ROLE.equals(target.getRole())) {
            acquireAdminGuardLock();
            if (repository.countByRole(ADMIN_ROLE) <= 1) {
                recordDenied(actorEmail, targetId, "USER_DELETED", "LAST_ADMINISTRATOR_PROTECTED");
                throw new LastAdministratorException("Impossible de supprimer le dernier administrateur.");
            }
        }

        try {
            repository.deleteById(targetId);
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            recordDenied(actorEmail, targetId, "USER_DELETED", "DELETION_CONFLICT");
            throw new UserDeletionConflictException(
                    "Suppression impossible : cet utilisateur est référencé par d'autres données.");
        }

        securityAuditService.record(SecurityEventType.USER_DELETED, SecurityEventResult.SUCCESS,
                SecurityAuditService.maskEmail(actorEmail), null, "user", String.valueOf(targetId), null);
    }

    private User loadTarget(String actorEmail, Long targetId, String action) {
        return repository.findById(targetId)
                .orElseThrow(() -> {
                    recordDenied(actorEmail, targetId, action, "TARGET_NOT_FOUND");
                    return new UserNotFoundException("Utilisateur introuvable : " + targetId);
                });
    }

    private void rejectSelfAction(String actorEmail, User target, String action, String message) {
        if (target.getEmail().equalsIgnoreCase(actorEmail)) {
            recordDenied(actorEmail, target.getId(), action, "SELF_ACTION_FORBIDDEN");
            throw new SelfActionForbiddenException(message);
        }
    }

    private void recordDenied(String actorEmail, Long targetId, String action, String reasonCode) {
        securityAuditService.record(SecurityEventType.ADMIN_ACTION_DENIED, SecurityEventResult.DENIED,
                SecurityAuditService.maskEmail(actorEmail), null, "user",
                targetId == null ? null : String.valueOf(targetId), action + ":" + reasonCode);
    }

    private void acquireAdminGuardLock() {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:key)")
                .setParameter("key", ADMIN_GUARD_LOCK_KEY)
                .getSingleResult();
    }
}
