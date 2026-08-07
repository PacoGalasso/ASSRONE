package ASSRONE.backend.service;

import ASSRONE.backend.audit.SecurityAuditService;
import ASSRONE.backend.audit.SecurityEventResult;
import ASSRONE.backend.audit.SecurityEventType;
import ASSRONE.backend.dto.RegisterRequest;
import ASSRONE.backend.dto.RegisterResponse;
import ASSRONE.backend.dto.UserDto;
import ASSRONE.backend.exception.UserAlreadyExistsException;
import ASSRONE.backend.exception.UsernameAlreadyExistsException;
import ASSRONE.backend.mapper.UserMapper;
import ASSRONE.backend.model.User;
import ASSRONE.backend.repository.UserInfoRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class UserInfoService implements UserDetailsService {

    private static final String USERNAME_UNIQUE_CONSTRAINT_NAME = "uk_users_username";

    private final UserInfoRepository repository;
    private final PasswordEncoder encoder;
    private final UserMapper userMapper;
    private final Clock clock;
    private final SecurityAuditService securityAuditService;
    private final EmailVerificationService emailVerificationService;

    @Autowired
    public UserInfoService(UserInfoRepository repository, PasswordEncoder encoder, UserMapper userMapper, Clock clock,
                            SecurityAuditService securityAuditService, EmailVerificationService emailVerificationService) {
        this.repository = repository;
        this.encoder = encoder;
        this.userMapper = userMapper;
        this.clock = clock;
        this.securityAuditService = securityAuditService;
        this.emailVerificationService = emailVerificationService;
    }

    // Method to load user details by username (email)
    @Override
    @NullMarked
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Fetch user from the database by email (username)
        Optional<ASSRONE.backend.model.User> userInfo = repository.findByEmail(username);

        if (userInfo.isEmpty()) {
            throw new UsernameNotFoundException("User not found with email: " + username);
        }

        // Convert UserInfo to UserDetails (UserInfoDetails)
        ASSRONE.backend.model.User user = userInfo.get();
        return new UserInfoDetails(user, clock);
    }

    public RegisterResponse addUser(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);

        if (repository.findByEmail(normalizedEmail).isPresent()) {
            throw new UserAlreadyExistsException("Un compte existe déjà avec l'email " + normalizedEmail);
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(normalizedEmail)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .password(encoder.encode(request.getPassword()))
                .role("USER")
                .isActive(true)
                .build();

        User saved;
        try {
            saved = repository.save(user);
        } catch (DataIntegrityViolationException ex) {
            // The pre-check above can't see a concurrent registration for the
            // same email landing between that read and this write; the unique
            // constraints on users.email and users.username are the real
            // guarantee. Unlike email, a chosen username collision here isn't a
            // race — the caller picked one already in use, and deserves a
            // distinct, actionable message rather than being told their email
            // (which may well be free) is the problem.
            if (isUsernameUniqueConstraintViolation(ex)) {
                throw new UsernameAlreadyExistsException(
                        "Le nom d'utilisateur " + request.getUsername() + " est déjà utilisé.");
            }
            throw new UserAlreadyExistsException("Un compte existe déjà avec l'email " + normalizedEmail);
        }

        securityAuditService.record(SecurityEventType.USER_CREATED, SecurityEventResult.SUCCESS,
                String.valueOf(saved.getId()), "ROLE_" + saved.getRole().toUpperCase(Locale.ROOT),
                "user", String.valueOf(saved.getId()), "SELF_REGISTRATION");

        emailVerificationService.issueTokenAndSendEmail(saved);

        return RegisterResponse.builder()
                .id(saved.getId())
                .username(saved.getUsername())
                .email(saved.getEmail())
                .firstName(saved.getFirstName())
                .lastName(saved.getLastName())
                .build();
    }

    private static boolean isUsernameUniqueConstraintViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof ConstraintViolationException constraintViolationException) {
            String constraintName = constraintViolationException.getConstraintName();
            return constraintName != null && constraintName.equalsIgnoreCase(USERNAME_UNIQUE_CONSTRAINT_NAME);
        }
        return false;
    }

    public List<UserDto> getAll() {
        return repository.findAll().stream().map(userMapper::toDto).toList();
    }
}