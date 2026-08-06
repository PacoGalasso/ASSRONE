package ASSRONE.backend.service;

import ASSRONE.backend.dto.ChangePasswordRequest;
import ASSRONE.backend.dto.UpdateProfileRequest;
import ASSRONE.backend.dto.UserProfileDto;
import ASSRONE.backend.exception.InvalidPasswordException;
import ASSRONE.backend.exception.UserAlreadyExistsException;
import ASSRONE.backend.mapper.UserProfileMapper;
import ASSRONE.backend.model.User;
import ASSRONE.backend.repository.UserInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
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

    @Value("${app.upload-dir}")
    private String uploadDir;

    public UserProfileDto getProfile(String email) {
        User user = findByEmailOrThrow(email);
        return userProfileMapper.toDto(user);
    }

    public UserProfileDto updateProfile(String email, UpdateProfileRequest request) {
        User user = findByEmailOrThrow(email);

        // Normalized the same way as registration (UserInfoService#addUser) and
        // login (UserController#normalizeEmail): without this, a member could
        // save their email with different casing/whitespace than what they type
        // at login (which always normalizes), locking themselves out.
        String normalizedNewEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (!normalizedNewEmail.equalsIgnoreCase(user.getEmail())) {
            userRepository.findByEmail(normalizedNewEmail).ifPresent(existing -> {
                throw new UserAlreadyExistsException("Un compte existe déjà avec l'email " + normalizedNewEmail);
            });
        }

        user.setUsername(request.getUsername());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(normalizedNewEmail);
        User saved = userRepository.save(user);
        return userProfileMapper.toDto(saved);
    }

    public void changePassword(String email, ChangePasswordRequest request) {
        User user = findByEmailOrThrow(email);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new InvalidPasswordException("Le mot de passe actuel est incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // A refresh token stolen before the password change must not remain
        // usable afterwards — force every other session to require a fresh login.
        refreshTokenService.revokeAllForUser(user.getId());
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
