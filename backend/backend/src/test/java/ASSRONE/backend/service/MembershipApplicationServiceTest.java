package ASSRONE.backend.service;

import ASSRONE.backend.dto.CreateMembershipApplicationRequest;
import ASSRONE.backend.dto.MembershipApplicationDto;
import ASSRONE.backend.event.MembershipApplicationSubmittedEvent;
import ASSRONE.backend.exception.MembershipApplicationAlreadyPendingException;
import ASSRONE.backend.exception.ResourceNotFoundException;
import ASSRONE.backend.exception.UserAlreadyExistsException;
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
    private MembershipEmailService membershipEmailService;

    @Mock
    private UserInfoRepository userInfoRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private MembershipApplicationService service() {
        return new MembershipApplicationService(
                repository, mapper, membershipEmailService, userInfoRepository, passwordEncoder, eventPublisher);
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
        verifyNoInteractions(membershipEmailService);
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
        verifyNoInteractions(membershipEmailService);
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
        verifyNoInteractions(membershipEmailService);
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
        verifyNoInteractions(membershipEmailService);
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
}
