package ASSRONE.backend.service;

import ASSRONE.backend.audit.AuditLogCapture;
import ASSRONE.backend.audit.SecurityAuditService;
import ASSRONE.backend.exception.LastAdministratorException;
import ASSRONE.backend.exception.SelfActionForbiddenException;
import ASSRONE.backend.exception.UserDeletionConflictException;
import ASSRONE.backend.exception.UserNotFoundException;
import ASSRONE.backend.model.User;
import ASSRONE.backend.model.UserRole;
import ASSRONE.backend.repository.UserInfoRepository;
import ASSRONE.backend.security.ClientIpResolver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserManagementServiceTest {

    @Mock
    private UserInfoRepository repository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query lockQuery;

    private AdminUserManagementService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserManagementService(repository, entityManager,
                new SecurityAuditService(new ClientIpResolver("")));
        lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(lockQuery);
        lenient().when(lockQuery.setParameter(anyString(), any())).thenReturn(lockQuery);
    }

    private static User user(Long id, String email, String role) {
        return User.builder().id(id).email(email).username("u" + id).password("hash").role(role).build();
    }

    @Test
    void administrateurModifieLeRoleDUnAutreUtilisateur() {
        User target = user(2L, "membre@assrone.ch", "USER");
        when(repository.findById(2L)).thenReturn(Optional.of(target));
        when(repository.updateRole(2L, "ADMIN")).thenReturn(1);

        service.changeRole("admin@assrone.ch", 2L, UserRole.ADMIN);

        verify(repository).updateRole(2L, "ADMIN");
    }

    @Test
    void miseAJourAffectantZeroLigneNEstJamaisConsidereeCommeUnSucces() {
        User target = user(2L, "membre@assrone.ch", "USER");
        when(repository.findById(2L)).thenReturn(Optional.of(target));
        when(repository.updateRole(2L, "ADMIN")).thenReturn(0);

        assertThatThrownBy(() -> service.changeRole("admin@assrone.ch", 2L, UserRole.ADMIN))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void utilisateurCibleInexistantLeveUneExceptionNonTrouve() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeRole("admin@assrone.ch", 99L, UserRole.ADMIN))
                .isInstanceOf(UserNotFoundException.class);
        verify(repository, never()).updateRole(any(), any());
    }

    @Test
    void administrateurNePeutPasModifierSonPropreRole() {
        User target = user(1L, "admin@assrone.ch", "ADMIN");
        when(repository.findById(1L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.changeRole("admin@assrone.ch", 1L, UserRole.USER))
                .isInstanceOf(SelfActionForbiddenException.class);
        verify(repository, never()).updateRole(any(), any());
    }

    @Test
    void dernierAdministrateurNePeutPasEtreRetrograde() {
        User target = user(2L, "admin2@assrone.ch", "ADMIN");
        when(repository.findById(2L)).thenReturn(Optional.of(target));
        when(repository.countByRole("ADMIN")).thenReturn(1L);

        assertThatThrownBy(() -> service.changeRole("admin1@assrone.ch", 2L, UserRole.USER))
                .isInstanceOf(LastAdministratorException.class);
        verify(repository, never()).updateRole(any(), any());
        verify(entityManager).createNativeQuery(eq("SELECT pg_advisory_xact_lock(:key)"));
    }

    @Test
    void retrograderUnAdministrateurQuandIlEnResteDAutresEstAutorise() {
        User target = user(2L, "admin2@assrone.ch", "ADMIN");
        when(repository.findById(2L)).thenReturn(Optional.of(target));
        when(repository.countByRole("ADMIN")).thenReturn(2L);
        when(repository.updateRole(2L, "USER")).thenReturn(1);

        service.changeRole("admin1@assrone.ch", 2L, UserRole.USER);

        verify(repository).updateRole(2L, "USER");
    }

    @Test
    void promouvoirUnUtilisateurNeVerifiePasLeCompteDAdministrateurs() {
        User target = user(2L, "membre@assrone.ch", "USER");
        when(repository.findById(2L)).thenReturn(Optional.of(target));
        when(repository.updateRole(2L, "ADMIN")).thenReturn(1);

        service.changeRole("admin@assrone.ch", 2L, UserRole.ADMIN);

        verify(repository, never()).countByRole(anyString());
    }

    @Test
    void administrateurSupprimeUnUtilisateur() {
        User target = user(2L, "membre@assrone.ch", "USER");
        when(repository.findById(2L)).thenReturn(Optional.of(target));

        service.deleteUser("admin@assrone.ch", 2L);

        verify(repository).deleteById(2L);
    }

    @Test
    void suppressionUtilisateurInexistantLeveUneExceptionNonTrouve() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteUser("admin@assrone.ch", 99L))
                .isInstanceOf(UserNotFoundException.class);
        verify(repository, never()).deleteById(any());
    }

    @Test
    void administrateurNePeutPasSeSupprimer() {
        User target = user(1L, "admin@assrone.ch", "ADMIN");
        when(repository.findById(1L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.deleteUser("admin@assrone.ch", 1L))
                .isInstanceOf(SelfActionForbiddenException.class);
        verify(repository, never()).deleteById(any());
    }

    @Test
    void dernierAdministrateurNePeutPasEtreSupprime() {
        User target = user(2L, "admin2@assrone.ch", "ADMIN");
        when(repository.findById(2L)).thenReturn(Optional.of(target));
        when(repository.countByRole("ADMIN")).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteUser("admin1@assrone.ch", 2L))
                .isInstanceOf(LastAdministratorException.class);
        verify(repository, never()).deleteById(any());
    }

    @Test
    void suppressionEnConflitAvecDesDonneesReferenceesRetourneUnConflit() {
        User target = user(2L, "membre@assrone.ch", "USER");
        when(repository.findById(2L)).thenReturn(Optional.of(target));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("fk violation"))
                .when(repository).deleteById(2L);

        assertThatThrownBy(() -> service.deleteUser("admin@assrone.ch", 2L))
                .isInstanceOf(UserDeletionConflictException.class);
    }

    // ===== Journalisation d'audit =====

    @Test
    void changementDeRoleReussiJournaliseRoleChangeAvecLeNouveauRoleEnReasonCode() {
        User target = user(2L, "membre@assrone.ch", "USER");
        when(repository.findById(2L)).thenReturn(Optional.of(target));
        when(repository.updateRole(2L, "ADMIN")).thenReturn(1);

        try (AuditLogCapture capture = new AuditLogCapture()) {
            service.changeRole("admin@assrone.ch", 2L, UserRole.ADMIN);

            String line = capture.messages().get(0);
            assertThat(line).contains("eventType=ROLE_CHANGE")
                    .contains("result=SUCCESS")
                    .contains("targetId=2")
                    .contains("reasonCode=ADMIN")
                    .doesNotContain("admin@assrone.ch");
        }
    }

    @Test
    void autoModificationDeRoleJournaliseAdminActionDenied() {
        User target = user(1L, "admin@assrone.ch", "ADMIN");
        when(repository.findById(1L)).thenReturn(Optional.of(target));

        try (AuditLogCapture capture = new AuditLogCapture()) {
            assertThatThrownBy(() -> service.changeRole("admin@assrone.ch", 1L, UserRole.USER))
                    .isInstanceOf(SelfActionForbiddenException.class);

            String line = capture.messages().get(0);
            assertThat(line).contains("eventType=ADMIN_ACTION_DENIED")
                    .contains("result=DENIED")
                    .contains("reasonCode=ROLE_CHANGE:SELF_ACTION_FORBIDDEN");
        }
    }

    @Test
    void retrogradationDuDernierAdministrateurJournaliseAdminActionDenied() {
        User target = user(2L, "admin2@assrone.ch", "ADMIN");
        when(repository.findById(2L)).thenReturn(Optional.of(target));
        when(repository.countByRole("ADMIN")).thenReturn(1L);

        try (AuditLogCapture capture = new AuditLogCapture()) {
            assertThatThrownBy(() -> service.changeRole("admin1@assrone.ch", 2L, UserRole.USER))
                    .isInstanceOf(LastAdministratorException.class);

            String line = capture.messages().get(0);
            assertThat(line).contains("eventType=ADMIN_ACTION_DENIED")
                    .contains("reasonCode=ROLE_CHANGE:LAST_ADMINISTRATOR_PROTECTED");
        }
    }

    @Test
    void suppressionReussieJournaliseUserDeleted() {
        User target = user(2L, "membre@assrone.ch", "USER");
        when(repository.findById(2L)).thenReturn(Optional.of(target));

        try (AuditLogCapture capture = new AuditLogCapture()) {
            service.deleteUser("admin@assrone.ch", 2L);

            String line = capture.messages().get(0);
            assertThat(line).contains("eventType=USER_DELETED")
                    .contains("result=SUCCESS")
                    .contains("targetId=2");
        }
    }

    @Test
    void suppressionSurUtilisateurInexistantJournaliseAdminActionDenied() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        try (AuditLogCapture capture = new AuditLogCapture()) {
            assertThatThrownBy(() -> service.deleteUser("admin@assrone.ch", 99L))
                    .isInstanceOf(UserNotFoundException.class);

            String line = capture.messages().get(0);
            assertThat(line).contains("eventType=ADMIN_ACTION_DENIED")
                    .contains("reasonCode=USER_DELETED:TARGET_NOT_FOUND");
        }
    }
}
