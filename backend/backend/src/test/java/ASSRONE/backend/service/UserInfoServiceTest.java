package ASSRONE.backend.service;

import ASSRONE.backend.audit.AuditLogCapture;
import ASSRONE.backend.audit.SecurityAuditService;
import ASSRONE.backend.dto.RegisterRequest;
import ASSRONE.backend.exception.UserAlreadyExistsException;
import ASSRONE.backend.exception.UsernameAlreadyExistsException;
import ASSRONE.backend.mapper.UserMapper;
import ASSRONE.backend.model.User;
import ASSRONE.backend.repository.UserInfoRepository;
import ASSRONE.backend.security.ClientIpResolver;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.AdditionalAnswers.returnsFirstArg;

@ExtendWith(MockitoExtension.class)
class UserInfoServiceTest {

    @Mock
    private UserInfoRepository repository;

    @Mock
    private PasswordEncoder encoder;

    @Mock
    private UserMapper userMapper;

    private SecurityAuditService securityAuditService;

    private UserInfoService service;

    @BeforeEach
    void setUp() {
        securityAuditService = new SecurityAuditService(new ClientIpResolver(""));
        service = new UserInfoService(repository, encoder, userMapper, Clock.systemDefaultZone(), securityAuditService);
    }

    private RegisterRequest validRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("jdupont");
        request.setEmail("Jean.Dupont@ASSRONE.ch");
        request.setFirstName("Jean");
        request.setLastName("Dupont");
        request.setPassword("motdepasse123");
        return request;
    }

    @Test
    void normaliseEmailAvantLaRechercheEtLaSauvegarde() {
        when(repository.findByEmail("jean.dupont@assrone.ch")).thenReturn(Optional.empty());
        when(encoder.encode("motdepasse123")).thenReturn("hash");
        when(repository.save(any())).thenAnswer(returnsFirstArg());

        service.addUser(validRequest());

        verify(repository).findByEmail("jean.dupont@assrone.ch");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("jean.dupont@assrone.ch");
    }

    @Test
    void creeUnCompteAvecLeRoleStandardEtActif() {
        when(repository.findByEmail(any())).thenReturn(Optional.empty());
        when(encoder.encode(any())).thenReturn("hash");
        when(repository.save(any())).thenAnswer(returnsFirstArg());

        service.addUser(validRequest());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo("USER");
        assertThat(captor.getValue().getIsActive()).isTrue();
    }

    @Test
    void encodeLeMotDePasseAvantSauvegarde() {
        when(repository.findByEmail(any())).thenReturn(Optional.empty());
        when(encoder.encode("motdepasse123")).thenReturn("motdepasse-hache");
        when(repository.save(any())).thenAnswer(returnsFirstArg());

        service.addUser(validRequest());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("motdepasse-hache");
    }

    @Test
    void refuseUnEmailDejaUtilise() {
        when(repository.findByEmail("jean.dupont@assrone.ch")).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> service.addUser(validRequest()))
                .isInstanceOf(UserAlreadyExistsException.class);

        verify(repository, Mockito.never()).save(any());
    }

    @Test
    void nomDUtilisateurDejaUtiliseLeveUsernameAlreadyExistsException() {
        when(repository.findByEmail("jean.dupont@assrone.ch")).thenReturn(Optional.empty());
        when(encoder.encode(any())).thenReturn("hash");
        ConstraintViolationException cause = new ConstraintViolationException(
                "duplicate key value violates unique constraint", null, "uk_users_username");
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("collision", cause));

        assertThatThrownBy(() -> service.addUser(validRequest()))
                .isInstanceOf(UsernameAlreadyExistsException.class)
                .hasMessage("Le nom d'utilisateur jdupont est déjà utilisé.");
    }

    @Test
    void collisionDIntegriteSansRapportAvecLeUsernameResteUneCollisionDEmail() {
        when(repository.findByEmail("jean.dupont@assrone.ch")).thenReturn(Optional.empty());
        when(encoder.encode(any())).thenReturn("hash");
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("collision sans cause connue"));

        assertThatThrownBy(() -> service.addUser(validRequest()))
                .isInstanceOf(UserAlreadyExistsException.class)
                .isNotInstanceOf(UsernameAlreadyExistsException.class);
    }

    @Test
    void aucunChampControleParLeServeurNeProvientDeLaRequete() {
        when(repository.findByEmail(any())).thenReturn(Optional.empty());
        when(encoder.encode(any())).thenReturn("hash");
        when(repository.save(any())).thenAnswer(returnsFirstArg());

        service.addUser(validRequest());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getId()).isNull();
        assertThat(saved.getRole()).isEqualTo("USER");
        assertThat(saved.getIsActive()).isTrue();
    }

    // ===== Journalisation d'audit (USER_CREATED) =====

    private static User savedUserWithId(Long id) {
        return User.builder()
                .id(id)
                .username("jdupont")
                .email("jean.dupont@assrone.ch")
                .firstName("Jean")
                .lastName("Dupont")
                .password("motdepasse-hache")
                .role("USER")
                .isActive(true)
                .build();
    }

    @Test
    void creationReussieJournaliseExactementUnEvenementUserCreated() {
        when(repository.findByEmail("jean.dupont@assrone.ch")).thenReturn(Optional.empty());
        when(encoder.encode(any())).thenReturn("motdepasse-hache");
        when(repository.save(any())).thenReturn(savedUserWithId(42L));

        try (AuditLogCapture capture = new AuditLogCapture()) {
            service.addUser(validRequest());

            assertThat(capture.messages()).hasSize(1);
            String line = capture.messages().get(0);
            assertThat(line).contains("eventType=USER_CREATED")
                    .contains("result=SUCCESS")
                    .contains("reasonCode=SELF_REGISTRATION");
        }
    }

    @Test
    void ligneUserCreatedReferenceLIdentifiantInterneDeLUtilisateurCree() {
        when(repository.findByEmail("jean.dupont@assrone.ch")).thenReturn(Optional.empty());
        when(encoder.encode(any())).thenReturn("motdepasse-hache");
        when(repository.save(any())).thenReturn(savedUserWithId(42L));

        try (AuditLogCapture capture = new AuditLogCapture()) {
            service.addUser(validRequest());

            String line = capture.messages().get(0);
            assertThat(line).contains("actorId=42").contains("targetType=user").contains("targetId=42");
        }
    }

    @Test
    void ligneUserCreatedNeContientJamaisLeMotDePasse() {
        when(repository.findByEmail("jean.dupont@assrone.ch")).thenReturn(Optional.empty());
        when(encoder.encode(any())).thenReturn("motdepasse-hache-tres-secrete");
        when(repository.save(any())).thenReturn(savedUserWithId(42L));

        try (AuditLogCapture capture = new AuditLogCapture()) {
            service.addUser(validRequest());

            String line = capture.messages().get(0);
            assertThat(line).doesNotContain("motdepasse-hache-tres-secrete")
                    .doesNotContain("motdepasse123");
        }
    }

    @Test
    void ligneUserCreatedNeSerialisePasLeDtoUtilisateurComplet() {
        when(repository.findByEmail("jean.dupont@assrone.ch")).thenReturn(Optional.empty());
        when(encoder.encode(any())).thenReturn("motdepasse-hache");
        when(repository.save(any())).thenReturn(savedUserWithId(42L));

        try (AuditLogCapture capture = new AuditLogCapture()) {
            service.addUser(validRequest());

            String line = capture.messages().get(0);
            assertThat(line).doesNotContain("jean.dupont@assrone.ch")
                    .doesNotContain("Jean")
                    .doesNotContain("Dupont");
        }
    }

    @Test
    void aucunEvenementJournaliseQuandLEmailExisteDeja() {
        when(repository.findByEmail("jean.dupont@assrone.ch")).thenReturn(Optional.of(new User()));

        try (AuditLogCapture capture = new AuditLogCapture()) {
            assertThatThrownBy(() -> service.addUser(validRequest()))
                    .isInstanceOf(UserAlreadyExistsException.class);

            assertThat(capture.messages()).isEmpty();
        }
        verifyNoInteractions(encoder);
    }

    @Test
    void aucunEvenementJournaliseQuandLaSauvegardeEchoueParCollisionDeNomDUtilisateur() {
        when(repository.findByEmail("jean.dupont@assrone.ch")).thenReturn(Optional.empty());
        when(encoder.encode(any())).thenReturn("hash");
        ConstraintViolationException cause = new ConstraintViolationException(
                "duplicate key value violates unique constraint", null, "uk_users_username");
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("collision", cause));

        try (AuditLogCapture capture = new AuditLogCapture()) {
            assertThatThrownBy(() -> service.addUser(validRequest()))
                    .isInstanceOf(UsernameAlreadyExistsException.class);

            assertThat(capture.messages()).isEmpty();
        }
    }

    @Test
    void aucunEvenementJournaliseQuandLaSauvegardeEchouePourUneAutreRaison() {
        when(repository.findByEmail("jean.dupont@assrone.ch")).thenReturn(Optional.empty());
        when(encoder.encode(any())).thenReturn("hash");
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("collision sans cause connue"));

        try (AuditLogCapture capture = new AuditLogCapture()) {
            assertThatThrownBy(() -> service.addUser(validRequest()))
                    .isInstanceOf(UserAlreadyExistsException.class);

            assertThat(capture.messages()).isEmpty();
        }
    }
}
