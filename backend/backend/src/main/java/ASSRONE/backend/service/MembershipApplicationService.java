package ASSRONE.backend.service;

import ASSRONE.backend.dto.CreateMembershipApplicationRequest;
import ASSRONE.backend.dto.MembershipApplicationDto;
import ASSRONE.backend.event.MembershipApplicationAcceptedEvent;
import ASSRONE.backend.event.MembershipApplicationSubmittedEvent;
import ASSRONE.backend.exception.MembershipApplicationAlreadyPendingException;
import ASSRONE.backend.exception.ResourceNotFoundException;
import ASSRONE.backend.exception.UserAlreadyExistsException;
import ASSRONE.backend.mapper.MembershipApplicationMapper;
import ASSRONE.backend.model.ApplicationStatus;
import ASSRONE.backend.model.MembershipApplication;
import ASSRONE.backend.model.User;
import ASSRONE.backend.repository.MembershipApplicationRepository;
import ASSRONE.backend.repository.UserInfoRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MembershipApplicationService {

    private static final String PENDING_UNIQUE_CONSTRAINT_NAME = "uk_membership_application_pending_email";
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int PASSWORD_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MembershipApplicationRepository repository;
    private final MembershipApplicationMapper mapper;
    private final UserInfoRepository userInfoRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public MembershipApplicationDto submit(CreateMembershipApplicationRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);

        if (userInfoRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new UserAlreadyExistsException("Un compte existe déjà avec l'email " + normalizedEmail);
        }

        if (repository.existsByEmailAndStatus(normalizedEmail, ApplicationStatus.PENDING)) {
            throw new MembershipApplicationAlreadyPendingException(
                    "Une demande d'adhésion est déjà en cours de traitement pour cet email.");
        }

        MembershipApplication entity = mapper.fromCreateRequest(request);
        entity.setEmail(normalizedEmail);

        try {
            repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            if (isPendingUniqueConstraintViolation(ex)) {
                throw new MembershipApplicationAlreadyPendingException(
                        "Une demande d'adhésion est déjà en cours de traitement pour cet email.");
            }
            throw ex;
        }

        eventPublisher.publishEvent(new MembershipApplicationSubmittedEvent(
                entity.getId(),
                entity.getFullName(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getMembershipType(),
                entity.getMessage()
        ));

        return mapper.toDto(entity);
    }

    private static boolean isPendingUniqueConstraintViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof ConstraintViolationException constraintViolationException) {
            String constraintName = constraintViolationException.getConstraintName();
            return constraintName != null && constraintName.equalsIgnoreCase(PENDING_UNIQUE_CONSTRAINT_NAME);
        }
        return false;
    }

    public List<MembershipApplicationDto> getAll() {
        return repository.findAllByOrderBySubmittedAtDesc()
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Transactional
    public MembershipApplicationDto accept(Long id) {
        MembershipApplication application = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Demande introuvable : " + id));

        if (userInfoRepository.findByEmail(application.getEmail()).isPresent()) {
            throw new UserAlreadyExistsException("Un compte existe déjà avec l'email " + application.getEmail());
        }

        String rawPassword = generateRandomPassword();
        String[] nameParts = application.getFullName().trim().split("\\s+", 2);
        String firstName = nameParts[0];
        String lastName = nameParts.length > 1 ? nameParts[1] : "";

        User user = User.builder()
                .email(application.getEmail())
                .username(application.getEmail().split("@")[0])
                .password(passwordEncoder.encode(rawPassword))
                .firstName(firstName)
                .lastName(lastName)
                .role("USER")
                .build();
        try {
            userInfoRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            // The pre-check above (findByEmail) can't see a concurrent accept()
            // for the same email landing between that read and this write; the
            // unique constraint on users.email is the real guarantee, and this
            // translates its violation into the same clean 409 the pre-check
            // already produces for the non-concurrent case.
            throw new UserAlreadyExistsException("Un compte existe déjà avec l'email " + application.getEmail());
        }

        application.setStatus(ApplicationStatus.APPROVED);
        MembershipApplication saved = repository.save(application);

        eventPublisher.publishEvent(new MembershipApplicationAcceptedEvent(
                saved.getId(), saved.getFullName(), saved.getEmail(), rawPassword));

        return mapper.toDto(saved);
    }

    @Transactional
    public MembershipApplicationDto reject(Long id) {
        MembershipApplication application = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Demande introuvable : " + id));
        application.setStatus(ApplicationStatus.REJECTED);
        return mapper.toDto(repository.save(application));
    }

    private String generateRandomPassword() {
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}
