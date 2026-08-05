package ASSRONE.backend.service;

import ASSRONE.backend.dto.CreateMembershipApplicationRequest;
import ASSRONE.backend.dto.MembershipApplicationDto;
import ASSRONE.backend.event.MembershipApplicationAcceptedEvent;
import ASSRONE.backend.event.MembershipApplicationSubmittedEvent;
import ASSRONE.backend.exception.MembershipApplicationAlreadyPendingException;
import ASSRONE.backend.exception.ResourceNotFoundException;
import ASSRONE.backend.exception.UserAlreadyExistsException;
import ASSRONE.backend.exception.UsernameAlreadyExistsException;
import ASSRONE.backend.mapper.MembershipApplicationMapper;
import ASSRONE.backend.model.ApplicationStatus;
import ASSRONE.backend.model.MembershipApplication;
import ASSRONE.backend.model.MembershipType;
import ASSRONE.backend.model.User;
import ASSRONE.backend.repository.MembershipApplicationRepository;
import ASSRONE.backend.repository.UserInfoRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MembershipApplicationServiceTest {

    @Mock
    private MembershipApplicationRepository repository;

    @Mock
    private MembershipApplicationMapper mapper;

    @Mock
    private UserInfoRepository userInfoRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private MembershipApplicationService service() {
        return new MembershipApplicationService(
                repository, mapper, userInfoRepository, passwordEncoder, eventPublisher);
    }

    private static CreateMembershipApplicationRequest requeteValide(String email) {
        return CreateMembershipApplicationRequest.builder()
                .fullName("Jean Dupont")
                .email(email)
                .membershipType(MembershipType.INDIVIDUEL)
                .charterAccepted(true)
                .build();
    }

    private static MembershipApplication entiteMappee(String email) {
        return MembershipApplication.builder()
                .fullName("Jean Dupont")
                .email(email)
                .membershipType(MembershipType.INDIVIDUEL)
                .charterAccepted(true)
                .build();
    }

    private static MembershipApplication candidaturePending(Long id, String email) {
        return MembershipApplication.builder()
                .id(id)
                .fullName("Jean Dupont")
                .email(email)
                .membershipType(MembershipType.INDIVIDUEL)
                .charterAccepted(true)
                .status(ApplicationStatus.PENDING)
                .build();
    }

    @Test
    void acceptAvecUnIdInexistantLeveResourceNotFound() {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().accept(42L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Demande introuvable : 42");
    }

    @Test
    void rejectAvecUnIdInexistantLeveResourceNotFound() {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().reject(42L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Demande introuvable : 42");
    }

    @Test
    void soumissionValideEstAccepteeEtPublieUnEvenementApplicatif() {
        when(userInfoRepository.findByEmail("jean.dupont@assrone.ch")).thenReturn(Optional.empty());
        when(repository.existsByEmailAndStatus("jean.dupont@assrone.ch", ApplicationStatus.PENDING)).thenReturn(false);
        MembershipApplication entity = entiteMappee("jean.dupont@assrone.ch");
        when(mapper.fromCreateRequest(any())).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(MembershipApplicationDto.builder().email("jean.dupont@assrone.ch").build());

        MembershipApplicationDto result = service().submit(requeteValide("jean.dupont@assrone.ch"));

        assertThat(result.getEmail()).isEqualTo("jean.dupont@assrone.ch");
        verify(repository).saveAndFlush(entity);

        ArgumentCaptor<MembershipApplicationSubmittedEvent> captor =
                ArgumentCaptor.forClass(MembershipApplicationSubmittedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo("jean.dupont@assrone.ch");
        assertThat(captor.getValue().fullName()).isEqualTo("Jean Dupont");
    }

    @Test
    void emailEstNormaliseAvantLesRecherchesEtLaSauvegarde() {
        when(userInfoRepository.findByEmail("jean.dupont@assrone.ch")).thenReturn(Optional.empty());
        when(repository.existsByEmailAndStatus("jean.dupont@assrone.ch", ApplicationStatus.PENDING)).thenReturn(false);
        MembershipApplication entity = entiteMappee("  Jean.Dupont@ASSRONE.ch  ");
        when(mapper.fromCreateRequest(any())).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(MembershipApplicationDto.builder().build());

        service().submit(requeteValide("  Jean.Dupont@ASSRONE.ch  "));

        verify(userInfoRepository).findByEmail("jean.dupont@assrone.ch");
        verify(repository).existsByEmailAndStatus("jean.dupont@assrone.ch", ApplicationStatus.PENDING);
        assertThat(entity.getEmail()).isEqualTo("jean.dupont@assrone.ch");
    }

    @Test
    void utilisateurExistantDansUsersEmpecheLaSoumission() {
        when(userInfoRepository.findByEmail("jean.dupont@assrone.ch"))
                .thenReturn(Optional.of(User.builder().email("jean.dupont@assrone.ch").username("jdupont").password("x").build()));

        assertThatThrownBy(() -> service().submit(requeteValide("jean.dupont@assrone.ch")))
                .isInstanceOf(UserAlreadyExistsException.class);

        verifyNoInteractions(repository);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void candidaturePendingExistanteEmpecheLaSoumission() {
        when(userInfoRepository.findByEmail("jean.dupont@assrone.ch")).thenReturn(Optional.empty());
        when(repository.existsByEmailAndStatus("jean.dupont@assrone.ch", ApplicationStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> service().submit(requeteValide("jean.dupont@assrone.ch")))
                .isInstanceOf(MembershipApplicationAlreadyPendingException.class)
                .hasMessage("Une demande d'adhésion est déjà en cours de traitement pour cet email.");

        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void soumissionAutoriseeMalgreUneAncienneCandidatureRejetee() {
        when(userInfoRepository.findByEmail("jean.dupont@assrone.ch")).thenReturn(Optional.empty());
        when(repository.existsByEmailAndStatus("jean.dupont@assrone.ch", ApplicationStatus.PENDING)).thenReturn(false);
        MembershipApplication entity = entiteMappee("jean.dupont@assrone.ch");
        when(mapper.fromCreateRequest(any())).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(MembershipApplicationDto.builder().build());

        service().submit(requeteValide("jean.dupont@assrone.ch"));

        verify(repository).saveAndFlush(entity);
        verify(eventPublisher, times(1)).publishEvent(any(MembershipApplicationSubmittedEvent.class));
    }

    @Test
    void soumissionAutoriseeMalgreUneAncienneCandidatureApprouveeSansUtilisateurActuel() {
        when(userInfoRepository.findByEmail("jean.dupont@assrone.ch")).thenReturn(Optional.empty());
        when(repository.existsByEmailAndStatus("jean.dupont@assrone.ch", ApplicationStatus.PENDING)).thenReturn(false);
        MembershipApplication entity = entiteMappee("jean.dupont@assrone.ch");
        when(mapper.fromCreateRequest(any())).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(MembershipApplicationDto.builder().build());

        service().submit(requeteValide("jean.dupont@assrone.ch"));

        verify(repository).saveAndFlush(entity);
        verify(eventPublisher, times(1)).publishEvent(any(MembershipApplicationSubmittedEvent.class));
    }

    @Test
    void collisionDeLaContrainteUniquePartielleEstTraduiteEnExceptionMetier() {
        when(userInfoRepository.findByEmail("jean.dupont@assrone.ch")).thenReturn(Optional.empty());
        when(repository.existsByEmailAndStatus("jean.dupont@assrone.ch", ApplicationStatus.PENDING)).thenReturn(false);
        MembershipApplication entity = entiteMappee("jean.dupont@assrone.ch");
        when(mapper.fromCreateRequest(any())).thenReturn(entity);
        ConstraintViolationException cause = new ConstraintViolationException(
                "duplicate key value violates unique constraint", null, "uk_membership_application_pending_email");
        when(repository.saveAndFlush(entity)).thenThrow(new DataIntegrityViolationException("collision", cause));

        assertThatThrownBy(() -> service().submit(requeteValide("jean.dupont@assrone.ch")))
                .isInstanceOf(MembershipApplicationAlreadyPendingException.class)
                .hasMessage("Une demande d'adhésion est déjà en cours de traitement pour cet email.");

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void collisionDIntegriteSansRapportAvecLaContraintePendingNestPasTraduite() {
        when(userInfoRepository.findByEmail("jean.dupont@assrone.ch")).thenReturn(Optional.empty());
        when(repository.existsByEmailAndStatus("jean.dupont@assrone.ch", ApplicationStatus.PENDING)).thenReturn(false);
        MembershipApplication entity = entiteMappee("jean.dupont@assrone.ch");
        when(mapper.fromCreateRequest(any())).thenReturn(entity);
        ConstraintViolationException cause = new ConstraintViolationException(
                "not-null constraint violated", null, "some_other_constraint");
        DataIntegrityViolationException autreErreur = new DataIntegrityViolationException("autre erreur", cause);
        when(repository.saveAndFlush(entity)).thenThrow(autreErreur);

        assertThatThrownBy(() -> service().submit(requeteValide("jean.dupont@assrone.ch")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(MembershipApplicationAlreadyPendingException.class);

        verifyNoInteractions(eventPublisher);
    }

    // ===== accept =====

    @Test
    void acceptAvecSuccesCreeUnCompteEtPublieUnEvenementDeBienvenue() {
        MembershipApplication application = candidaturePending(1L, "jean.dupont@assrone.ch");
        when(repository.findById(1L)).thenReturn(Optional.of(application));
        when(userInfoRepository.findByEmail("jean.dupont@assrone.ch")).thenReturn(Optional.empty());
        when(userInfoRepository.existsByUsername("jean.dupont")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("mot-de-passe-hache");
        when(repository.save(application)).thenReturn(application);
        when(mapper.toDto(application)).thenReturn(
                MembershipApplicationDto.builder().email("jean.dupont@assrone.ch").status(ApplicationStatus.APPROVED).build());

        MembershipApplicationDto result = service().accept(1L);

        assertThat(result.getEmail()).isEqualTo("jean.dupont@assrone.ch");
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPROVED);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userInfoRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("jean.dupont@assrone.ch");
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("jean.dupont");
        assertThat(userCaptor.getValue().getRole()).isEqualTo("USER");
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("mot-de-passe-hache");

        ArgumentCaptor<MembershipApplicationAcceptedEvent> eventCaptor =
                ArgumentCaptor.forClass(MembershipApplicationAcceptedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().applicationId()).isEqualTo(1L);
        assertThat(eventCaptor.getValue().fullName()).isEqualTo("Jean Dupont");
        assertThat(eventCaptor.getValue().email()).isEqualTo("jean.dupont@assrone.ch");
        assertThat(eventCaptor.getValue().rawPassword()).isNotBlank();
    }

    @Test
    void acceptAvecUnCompteDejaExistantEstRefuseSansCreerNiPublier() {
        MembershipApplication application = candidaturePending(1L, "jean.dupont@assrone.ch");
        when(repository.findById(1L)).thenReturn(Optional.of(application));
        when(userInfoRepository.findByEmail("jean.dupont@assrone.ch")).thenReturn(
                Optional.of(User.builder().email("jean.dupont@assrone.ch").username("jdupont").password("x").build()));

        assertThatThrownBy(() -> service().accept(1L))
                .isInstanceOf(UserAlreadyExistsException.class);

        verify(userInfoRepository, never()).save(any());
        verify(repository, never()).save(any(MembershipApplication.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void acceptAvecCollisionConcurrentielleSurLEmailEstTraduiteEnExceptionMetier() {
        // Le pré-contrôle (findByEmail) passe, mais l'écriture elle-même est en
        // conflit : simule une deuxième candidature pour le même email acceptée
        // entre le pré-contrôle et la sauvegarde de celle-ci.
        MembershipApplication application = candidaturePending(1L, "jean.dupont@assrone.ch");
        when(repository.findById(1L)).thenReturn(Optional.of(application));
        when(userInfoRepository.findByEmail("jean.dupont@assrone.ch")).thenReturn(Optional.empty());
        when(userInfoRepository.existsByUsername("jean.dupont")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("mot-de-passe-hache");
        when(userInfoRepository.save(any())).thenThrow(new DataIntegrityViolationException("collision"));

        assertThatThrownBy(() -> service().accept(1L))
                .isInstanceOf(UserAlreadyExistsException.class);

        verify(repository, never()).save(any(MembershipApplication.class));
        verifyNoInteractions(eventPublisher);
    }

    // ===== accept : collisions de username généré depuis la partie locale de l'email =====

    @Test
    void acceptGenereUnUsernameDistinctQuandLaPartieLocaleDeLEmailEstDejaPrise() {
        // Un compte "pacogalasso" (pacogalasso@gmail.com) existe déjà ; la
        // candidature en cours porte un email DIFFÉRENT mais partageant la même
        // partie locale. Les deux comptes doivent rester distincts et autorisés.
        MembershipApplication application = candidaturePending(1L, "pacogalasso@icloud.com");
        when(repository.findById(1L)).thenReturn(Optional.of(application));
        when(userInfoRepository.findByEmail("pacogalasso@icloud.com")).thenReturn(Optional.empty());
        when(userInfoRepository.existsByUsername("pacogalasso")).thenReturn(true);
        when(userInfoRepository.existsByUsername("pacogalasso2")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("mot-de-passe-hache");
        when(repository.save(application)).thenReturn(application);
        when(mapper.toDto(application)).thenReturn(
                MembershipApplicationDto.builder().email("pacogalasso@icloud.com").status(ApplicationStatus.APPROVED).build());

        service().accept(1L);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userInfoRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("pacogalasso@icloud.com");
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("pacogalasso2");
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
        verify(eventPublisher, times(1)).publishEvent(any(MembershipApplicationAcceptedEvent.class));
    }

    @Test
    void acceptGenereLeTroisiemeUsernameDisponibleApresPlusieursCollisionsSuccessives() {
        MembershipApplication application = candidaturePending(1L, "pacogalasso@outlook.com");
        when(repository.findById(1L)).thenReturn(Optional.of(application));
        when(userInfoRepository.findByEmail("pacogalasso@outlook.com")).thenReturn(Optional.empty());
        when(userInfoRepository.existsByUsername("pacogalasso")).thenReturn(true);
        when(userInfoRepository.existsByUsername("pacogalasso2")).thenReturn(true);
        when(userInfoRepository.existsByUsername("pacogalasso3")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("mot-de-passe-hache");
        when(repository.save(application)).thenReturn(application);
        when(mapper.toDto(application)).thenReturn(MembershipApplicationDto.builder().build());

        service().accept(1L);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userInfoRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("pacogalasso3");
    }

    @Test
    void acceptAvecCollisionConcurrentielleSurLeUsernameLorsDeLInsertionLeveUsernameAlreadyExistsSansApprouverNiPublier() {
        // Le pré-contrôle (existsByUsername) indique le candidat libre, mais une
        // autre requête l'a pris entre ce contrôle et l'écriture elle-même.
        MembershipApplication application = candidaturePending(1L, "pacogalasso@icloud.com");
        when(repository.findById(1L)).thenReturn(Optional.of(application));
        when(userInfoRepository.findByEmail("pacogalasso@icloud.com")).thenReturn(Optional.empty());
        when(userInfoRepository.existsByUsername("pacogalasso")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("mot-de-passe-hache");
        ConstraintViolationException cause = new ConstraintViolationException(
                "duplicate key value violates unique constraint", null, "uk_users_username");
        when(userInfoRepository.save(any())).thenThrow(new DataIntegrityViolationException("collision", cause));

        assertThatThrownBy(() -> service().accept(1L))
                .isInstanceOf(UsernameAlreadyExistsException.class);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.PENDING);
        verify(repository, never()).save(any(MembershipApplication.class));
        verifyNoInteractions(eventPublisher);
    }

    // ===== reject =====

    @Test
    void rejectAvecSuccesMarqueLaCandidatureRejetee() {
        MembershipApplication application = candidaturePending(1L, "jean.dupont@assrone.ch");
        when(repository.findById(1L)).thenReturn(Optional.of(application));
        when(repository.save(application)).thenReturn(application);
        when(mapper.toDto(application)).thenReturn(
                MembershipApplicationDto.builder().email("jean.dupont@assrone.ch").status(ApplicationStatus.REJECTED).build());

        MembershipApplicationDto result = service().reject(1L);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
    }
}
