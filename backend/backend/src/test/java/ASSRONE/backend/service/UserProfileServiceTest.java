package ASSRONE.backend.service;

import ASSRONE.backend.dto.ChangePasswordRequest;
import ASSRONE.backend.exception.InvalidPasswordException;
import ASSRONE.backend.mapper.UserProfileMapper;
import ASSRONE.backend.model.User;
import ASSRONE.backend.repository.UserInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserInfoRepository userRepository;

    @Mock
    private UserProfileMapper userProfileMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserProfileService service;

    @BeforeEach
    void setUp() {
        service = new UserProfileService(userRepository, userProfileMapper, passwordEncoder);
    }

    private User existingUser() {
        User user = new User();
        user.setEmail("membre@assrone.ch");
        user.setPassword("mot-de-passe-actuel-hache");
        return user;
    }

    @Test
    void motDePasseActuelIncorrectLeveInvalidPasswordException() {
        User user = existingUser();
        when(userRepository.findByEmail("membre@assrone.ch")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("mauvais-mot-de-passe", user.getPassword())).thenReturn(false);
        ChangePasswordRequest request = new ChangePasswordRequest("mauvais-mot-de-passe", "nouveauMotDePasse123");

        assertThatThrownBy(() -> service.changePassword("membre@assrone.ch", request))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessage("Le mot de passe actuel est incorrect");
    }

    @Test
    void motDePasseActuelIncorrectNeSauvegardeRien() {
        User user = existingUser();
        when(userRepository.findByEmail("membre@assrone.ch")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("mauvais-mot-de-passe", user.getPassword())).thenReturn(false);
        ChangePasswordRequest request = new ChangePasswordRequest("mauvais-mot-de-passe", "nouveauMotDePasse123");

        assertThatThrownBy(() -> service.changePassword("membre@assrone.ch", request))
                .isInstanceOf(InvalidPasswordException.class);

        verify(userRepository, Mockito.never()).save(any());
    }

    @Test
    void motDePasseActuelCorrectEncodeEtSauvegardeLeNouveauMotDePasse() {
        User user = existingUser();
        when(userRepository.findByEmail("membre@assrone.ch")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bon-mot-de-passe", user.getPassword())).thenReturn(true);
        when(passwordEncoder.encode("nouveauMotDePasse123")).thenReturn("nouveau-mot-de-passe-hache");
        ChangePasswordRequest request = new ChangePasswordRequest("bon-mot-de-passe", "nouveauMotDePasse123");

        service.changePassword("membre@assrone.ch", request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("nouveau-mot-de-passe-hache");
    }
}
