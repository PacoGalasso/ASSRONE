package ASSRONE.backend.service;

import ASSRONE.backend.audit.SecurityAuditService;
import ASSRONE.backend.audit.SecurityEventResult;
import ASSRONE.backend.audit.SecurityEventType;
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
import ASSRONE.backend.model.User;
import ASSRONE.backend.repository.MembershipApplicationRepository;
import ASSRONE.backend.repository.UserInfoRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MembershipApplicationService {

    private static final String PENDING_UNIQUE_CONSTRAINT_NAME = "uk_membership_application_pending_email";
    private static final String USERNAME_UNIQUE_CONSTRAINT_NAME = "uk_users_username";
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int PASSWORD_LENGTH = 12;
    private static final int MAX_USERNAME_SUFFIX_ATTEMPTS = 1000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MembershipApplicationRepository repository;
    private final MembershipApplicationMapper mapper;
    private final UserInfoRepository userInfoRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final SecurityAuditService securityAuditService;
    private final Clock clock;

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

    private static boolean isUsernameUniqueConstraintViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof ConstraintViolationException constraintViolationException) {
            String constraintName = constraintViolationException.getConstraintName();
            return constraintName != null && constraintName.equalsIgnoreCase(USERNAME_UNIQUE_CONSTRAINT_NAME);
        }
        return false;
    }

    /**
     * Preserves the email's local part as the username when it's free (the
     * existing, expected behavior), and only appends a numeric suffix once it
     * collides with an already-registered username — never on name/surname,
     * only on the generated username itself. This is a best-effort pre-check:
     * the users.username unique constraint (V6 migration) is what actually
     * guarantees no two accounts ever end up with the same username, even
     * under a lost race against this check.
     */
    private String findAvailableUsername(String baseUsername) {
        if (!userInfoRepository.existsByUsername(baseUsername)) {
            return baseUsername;
        }
        for (int suffix = 2; suffix <= MAX_USERNAME_SUFFIX_ATTEMPTS; suffix++) {
            String candidate = baseUsername + suffix;
            if (!userInfoRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Impossible de générer un nom d'utilisateur unique à partir de '"
                + baseUsername + "' après " + MAX_USERNAME_SUFFIX_ATTEMPTS + " tentatives.");
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
        String username = findAvailableUsername(application.getEmail().split("@")[0]);

        // The only normal account-creation path: an administrator has already
        // reviewed this application against the email address it was submitted
        // with, so there is nothing left for a token-based email-verification
        // round trip to prove. Setting this immediately — rather than leaving
        // it null pending a verification email that this path never sends —
        // is what makes the very next step (credentials delivery, then login)
        // actually reachable; a null value here would silently and permanently
        // lock the new member out (see UserController#authenticateAndGetToken).
        User user = User.builder()
                .email(application.getEmail())
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .firstName(firstName)
                .lastName(lastName)
                .role("USER")
                .emailVerifiedAt(LocalDateTime.now(clock))
                .build();
        try {
            userInfoRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            // The pre-checks above (findByEmail, findAvailableUsername) can't see
            // a concurrent accept()/registration landing between that read and
            // this write; the unique constraints on users.email and
            // users.username are the real guarantee. A username collision here
            // means another request grabbed the exact candidate we just picked
            // as available, microseconds earlier — distinguished from an email
            // collision by constraint name, since both can fail on this same
            // INSERT and they mean different things to the caller.
            if (isUsernameUniqueConstraintViolation(ex)) {
                throw new UsernameAlreadyExistsException(
                        "Une collision de nom d'utilisateur est survenue pendant la création du compte. Veuillez réessayer.");
            }
            throw new UserAlreadyExistsException("Un compte existe déjà avec l'email " + application.getEmail());
        }

        application.setStatus(ApplicationStatus.APPROVED);
        MembershipApplication saved = repository.save(application);

        eventPublisher.publishEvent(new MembershipApplicationAcceptedEvent(
                saved.getId(), saved.getFullName(), saved.getEmail(), rawPassword));

        securityAuditService.record(SecurityEventType.MEMBERSHIP_ACCEPTED, SecurityEventResult.SUCCESS,
                currentActorId(), null, "membershipApplication", String.valueOf(saved.getId()), null);
        return mapper.toDto(saved);
    }

    @Transactional
    public MembershipApplicationDto reject(Long id) {
        MembershipApplication application = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Demande introuvable : " + id));
        application.setStatus(ApplicationStatus.REJECTED);
        MembershipApplication saved = repository.save(application);

        securityAuditService.record(SecurityEventType.MEMBERSHIP_REJECTED, SecurityEventResult.SUCCESS,
                currentActorId(), null, "membershipApplication", String.valueOf(saved.getId()), null);
        return mapper.toDto(saved);
    }

    private static String currentActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? SecurityAuditService.maskEmail(authentication.getName()) : "-";
    }

    private String generateRandomPassword() {
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}
