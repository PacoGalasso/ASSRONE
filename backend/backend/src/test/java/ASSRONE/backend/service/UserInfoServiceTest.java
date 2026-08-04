package ASSRONE.backend.service;

import ASSRONE.backend.dto.RegisterRequest;
import ASSRONE.backend.exception.UserAlreadyExistsException;
import ASSRONE.backend.mapper.UserMapper;
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

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserInfoServiceTest {

    @Mock
    private UserInfoRepository repository;

    @Mock
    private PasswordEncoder encoder;

    @Mock
    private UserMapper userMapper;

    private UserInfoService service;

    @BeforeEach
    void setUp() {
        service = new UserInfoService(repository, encoder, userMapper, Clock.systemDefaultZone());
    }

    private RegisterRequest validRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("jdupont");
        request.setEmail("Jean.Dupont@ASSRONE.ch");
        request.setFirstName("Jean");
        request.setLastName("Dupont");
        request.setPassword("motdepasse123");
        return request;
    }

    @Test
    void normaliseEmailAvantLaRechercheEtLaSauvegarde() {
        when(repository.findByEmail("jean.dupont@assrone.ch")).thenReturn(Optional.empty());
        when(encoder.encode("motdepasse123")).thenReturn("hash");

        service.addUser(validRequest());

        verify(repository).findByEmail("jean.dupont@assrone.ch");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("jean.dupont@assrone.ch");
    }

    @Test
    void creeUnCompteAvecLeRoleStandardEtActif() {
        when(repository.findByEmail(any())).thenReturn(Optional.empty());
        when(encoder.encode(any())).thenReturn("hash");

        service.addUser(validRequest());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo("USER");
        assertThat(captor.getValue().getIsActive()).isTrue();
    }

    @Test
    void encodeLeMotDePasseAvantSauvegarde() {
        when(repository.findByEmail(any())).thenReturn(Optional.empty());
        when(encoder.encode("motdepasse123")).thenReturn("motdepasse-hache");

        service.addUser(validRequest());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("motdepasse-hache");
    }

    @Test
    void refuseUnEmailDejaUtilise() {
        when(repository.findByEmail("jean.dupont@assrone.ch")).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> service.addUser(validRequest()))
                .isInstanceOf(UserAlreadyExistsException.class);

        verify(repository, Mockito.never()).save(any());
    }

    @Test
    void aucunChampControleParLeServeurNeProvientDeLaRequete() {
        when(repository.findByEmail(any())).thenReturn(Optional.empty());
        when(encoder.encode(any())).thenReturn("hash");

        service.addUser(validRequest());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getId()).isNull();
        assertThat(saved.getRole()).isEqualTo("USER");
        assertThat(saved.getIsActive()).isTrue();
    }
}
