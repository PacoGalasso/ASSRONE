package ASSRONE.backend.service;

import ASSRONE.backend.dto.ChangePasswordRequest;
import ASSRONE.backend.dto.UpdateProfileRequest;
import ASSRONE.backend.dto.UserProfileDto;
import ASSRONE.backend.mapper.UserProfileMapper;
import ASSRONE.backend.model.User;
import ASSRONE.backend.repository.UserInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserInfoRepository userRepository;
    private final UserProfileMapper userProfileMapper;
    private final PasswordEncoder passwordEncoder;

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

    private User findByEmailOrThrow(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable : " + email));
    }
}
