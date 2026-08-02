package ASSRONE.backend.service;

import ASSRONE.backend.dto.CreateMembershipApplicationRequest;
import ASSRONE.backend.dto.MembershipApplicationDto;
import ASSRONE.backend.exception.UserAlreadyExistsException;
import ASSRONE.backend.mapper.MembershipApplicationMapper;
import ASSRONE.backend.model.ApplicationStatus;
import ASSRONE.backend.model.MembershipApplication;
import ASSRONE.backend.model.User;
import ASSRONE.backend.repository.MembershipApplicationRepository;
import ASSRONE.backend.repository.UserInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MembershipApplicationService {

    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int PASSWORD_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MembershipApplicationRepository repository;
    private final MembershipApplicationMapper mapper;
    private final MembershipEmailService membershipEmailService;
    private final UserInfoRepository userInfoRepository;
    private final PasswordEncoder passwordEncoder;

    public MembershipApplicationDto submit(CreateMembershipApplicationRequest request) {
        MembershipApplication entity = mapper.fromCreateRequest(request);
        MembershipApplication saved = repository.save(entity);
        membershipEmailService.sendApplicationNotification(saved);
        return mapper.toDto(saved);
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
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable : " + id));

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
        userInfoRepository.save(user);

        application.setStatus(ApplicationStatus.APPROVED);
        MembershipApplication saved = repository.save(application);

        membershipEmailService.sendAccountCreated(application, rawPassword);

        return mapper.toDto(saved);
    }

    public MembershipApplicationDto reject(Long id) {
        MembershipApplication application = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable : " + id));
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
