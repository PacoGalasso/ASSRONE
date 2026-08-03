package ASSRONE.backend.service;

import ASSRONE.backend.dto.RegisterRequest;
import ASSRONE.backend.dto.UserDto;
import ASSRONE.backend.exception.UserAlreadyExistsException;
import ASSRONE.backend.mapper.UserMapper;
import ASSRONE.backend.model.User;
import ASSRONE.backend.repository.UserInfoRepository;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class UserInfoService implements UserDetailsService {

    private final UserInfoRepository repository;
    private final PasswordEncoder encoder;
    private final UserMapper userMapper;

    @Autowired
    public UserInfoService(UserInfoRepository repository, PasswordEncoder encoder, UserMapper userMapper) {
        this.repository = repository;
        this.encoder = encoder;
        this.userMapper = userMapper;
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
        return new UserInfoDetails(user);
    }

    public String addUser(RegisterRequest request) {
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

        repository.save(user);
        return "User added successfully!";
    }

    public List<UserDto> getAll() {
        return repository.findAll().stream().map(userMapper::toDto).toList();
    }
}