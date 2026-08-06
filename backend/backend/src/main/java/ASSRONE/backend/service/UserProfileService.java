package ASSRONE.backend.service;

import ASSRONE.backend.audit.SecurityAuditService;
import ASSRONE.backend.audit.SecurityEventResult;
import ASSRONE.backend.audit.SecurityEventType;
import ASSRONE.backend.dto.ChangePasswordRequest;
import ASSRONE.backend.dto.UpdateProfileRequest;
import ASSRONE.backend.dto.UserProfileDto;
import ASSRONE.backend.exception.InvalidPasswordException;
import ASSRONE.backend.exception.UserAlreadyExistsException;
import ASSRONE.backend.mapper.UserProfileMapper;
import ASSRONE.backend.model.User;
import ASSRONE.backend.repository.UserInfoRepository;
import ASSRONE.backend.security.EmailNormalizer;
import ASSRONE.backend.security.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserInfoRepository userRepository;
    private final UserProfileMapper userProfileMapper;
    private final PasswordEncoder passwordEncoder;
    private final AvatarImageInspector avatarImageInspector;
    private final RefreshTokenService refreshTokenService;
    private final SecurityAuditService securityAuditService;
    private final EmailVerificationService emailVerificationService;

    @Value("${app.upload-dir}")
    private String uploadDir;

    public UserProfileDto getProfile(String email) {
        User user = findByEmailOrThrow(email);
        return userProfileMapper.toDto(user);
    }

    @Transactional
    public UserProfileDto updateProfile(String email, UpdateProfileRequest request) {
        User user = findByEmailOrThrow(email);

        // Normalized the same way as registration (UserInfoService#addUser) and
        // login (UserController#normalizeEmail): without this, a member could
        // save their email with different casing/whitespace than what they type
        // at login (which always normalizes), locking themselves out.
        String normalizedNewEmail = EmailNormalizer.normalize(request.getEmail());
        boolean emailChanged = !normalizedNewEmail.equalsIgnoreCase(user.getEmail());

        if (emailChanged) {
            // Changing the account's email is sensitive enough to require
            // re-proving the password, exactly like changePassword() requires
            // the current password — otherwise a hijacked, still-logged-in
            // session could silently redirect the account to an attacker's
            // inbox.
            if (request.getCurrentPassword() == null
                    || !passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                securityAuditService.record(SecurityEventType.EMAIL_CHANGE_REQUESTED, SecurityEventResult.DENIED,
                        String.valueOf(user.getId()), null, "user", String.valueOf(user.getId()), "CURRENT_PASSWORD_INCORRECT");
                throw new InvalidPasswordException("Le mot de passe actuel est incorrect");
            }
            userRepository.findByEmail(normalizedNewEmail).ifPresent(existing -> {
                throw new UserAlreadyExistsException("Un compte existe déjà avec l'email " + normalizedNewEmail);
            });
        }

        user.setUsername(request.getUsername());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        if (emailChanged) {
            user.setEmail(normalizedNewEmail);
            // The new address is unproven until its own verification link is
            // clicked — never inherit the old address's verified status.
            user.setEmailVerifiedAt(null);
        }
        User saved = userRepository.save(user);

        if (emailChanged) {
            // A hijacked session that changes the email must not keep any
            // session alive afterwards, including the one making this
            // request — mirrors changePassword()'s full revocation.
            refreshTokenService.revokeAllForUser(user.getId());
            emailVerificationService.issueTokenAndSendEmail(saved, "EMAIL_CHANGED");
            securityAuditService.record(SecurityEventType.EMAIL_CHANGE_REQUESTED, SecurityEventResult.SUCCESS,
                    String.valueOf(user.getId()), null, "user", String.valueOf(user.getId()), null);
        }

        return userProfileMapper.toDto(saved);
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = findByEmailOrThrow(email);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            securityAuditService.record(SecurityEventType.PASSWORD_CHANGE_FAILED, SecurityEventResult.DENIED,
                    String.valueOf(user.getId()), null, "user", String.valueOf(user.getId()), "CURRENT_PASSWORD_INCORRECT");
            throw new InvalidPasswordException("Le mot de passe actuel est incorrect");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            securityAuditService.record(SecurityEventType.PASSWORD_CHANGE_FAILED, SecurityEventResult.DENIED,
                    String.valueOf(user.getId()), null, "user", String.valueOf(user.getId()), "SAME_AS_CURRENT");
            throw new InvalidPasswordException("Le nouveau mot de passe doit être différent de l'actuel.");
        }
        PasswordPolicy.validate(request.getNewPassword());

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // A refresh token stolen before the password change must not remain
        // usable afterwards — force every other session (including the one
        // that just made this change) to require a fresh login.
        refreshTokenService.revokeAllForUser(user.getId());

        securityAuditService.record(SecurityEventType.PASSWORD_CHANGED, SecurityEventResult.SUCCESS,
                String.valueOf(user.getId()), null, "user", String.valueOf(user.getId()), null);
    }

    public void uploadAvatar(String email, MultipartFile file) throws IOException {
        ValidatedAvatar validatedAvatar = avatarImageInspector.inspect(file.getBytes());

        User user = findByEmailOrThrow(email);
        String previousAvatarFilename = user.getAvatarFilename();

        String storedFilename = UUID.randomUUID() + validatedAvatar.format().extension();
        Path target = resolveAvatarPath(storedFilename);
        Files.write(target, validatedAvatar.content());

        user.setAvatarFilename(storedFilename);
        try {
            userRepository.save(user);
        } catch (RuntimeException ex) {
            deleteQuietly(target);
            user.setAvatarFilename(previousAvatarFilename);
            throw ex;
        }

        if (previousAvatarFilename != null) {
            deleteQuietly(avatarDirectory().resolve(previousAvatarFilename));
        }
    }

    private Path avatarDirectory() {
        return Paths.get(uploadDir, "avatars").toAbsolutePath().normalize();
    }

    private Path resolveAvatarPath(String storedFilename) throws IOException {
        Path root = avatarDirectory();
        if (!Files.exists(root)) {
            Files.createDirectories(root);
        }

        Path target = root.resolve(storedFilename).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalStateException("Chemin d'avatar résolu hors du répertoire autorisé.");
        }
        return target;
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Échec du nettoyage d'un fichier avatar temporaire.");
        }
    }

    // Returns null both when the user has no avatar on record and when the
    // record points at a file no longer on disk (e.g. removed outside the
    // app, or lost with the upload volume) — the controller already treats a
    // null Resource as "no avatar" (404). Without this existence check, a
    // stale reference would reach the client as an uncaught
    // FileNotFoundException from Resource#contentLength() during response
    // serialization, past the point any @ExceptionHandler can intercept it.
    public Resource loadAvatar(String email) throws MalformedURLException {
        User user = findByEmailOrThrow(email);
        if (user.getAvatarFilename() == null) {
            return null;
        }
        Path path = Paths.get(uploadDir, "avatars").resolve(user.getAvatarFilename());
        if (!Files.exists(path)) {
            return null;
        }
        return new UrlResource(path.toUri());
    }

    private User findByEmailOrThrow(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable : " + email));
    }
}
