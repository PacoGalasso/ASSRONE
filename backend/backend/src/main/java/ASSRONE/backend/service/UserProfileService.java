package ASSRONE.backend.service;

import ASSRONE.backend.dto.ChangePasswordRequest;
import ASSRONE.backend.dto.UpdateProfileRequest;
import ASSRONE.backend.dto.UserProfileDto;
import ASSRONE.backend.mapper.UserProfileMapper;
import ASSRONE.backend.model.User;
import ASSRONE.backend.repository.UserInfoRepository;
import lombok.RequiredArgsConstructor;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserInfoRepository userRepository;
    private final UserProfileMapper userProfileMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.upload-dir}")
    private String uploadDir;

    public UserProfileDto getProfile(String email) {
        User user = findByEmailOrThrow(email);
        return userProfileMapper.toDto(user);
    }

    public UserProfileDto updateProfile(String email, UpdateProfileRequest request) {
        User user = findByEmailOrThrow(email);
        user.setUsername(request.getUsername());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        User saved = userRepository.save(user);
        return userProfileMapper.toDto(saved);
    }

    public void changePassword(String email, ChangePasswordRequest request) {
        User user = findByEmailOrThrow(email);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Le mot de passe actuel est incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    public void uploadAvatar(String email, MultipartFile file) throws IOException {
        User user = findByEmailOrThrow(email);

        Path avatarDir = Paths.get(uploadDir, "avatars");
        if (!Files.exists(avatarDir)) {
            Files.createDirectories(avatarDir);
        }

        String extension = "";
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }
        String storedFilename = UUID.randomUUID() + extension;
        Files.copy(file.getInputStream(), avatarDir.resolve(storedFilename));

        if (user.getAvatarFilename() != null) {
            Files.deleteIfExists(avatarDir.resolve(user.getAvatarFilename()));
        }

        user.setAvatarFilename(storedFilename);
        userRepository.save(user);
    }

    public Resource loadAvatar(String email) throws MalformedURLException {
        User user = findByEmailOrThrow(email);
        if (user.getAvatarFilename() == null) {
            throw new IllegalArgumentException("Aucune photo de profil");
        }
        Path path = Paths.get(uploadDir, "avatars").resolve(user.getAvatarFilename());
        return new UrlResource(path.toUri());
    }

    private User findByEmailOrThrow(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable : " + email));
    }
}
