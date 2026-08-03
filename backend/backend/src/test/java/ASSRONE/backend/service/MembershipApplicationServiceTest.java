package ASSRONE.backend.service;

import ASSRONE.backend.exception.ResourceNotFoundException;
import ASSRONE.backend.mapper.MembershipApplicationMapper;
import ASSRONE.backend.repository.MembershipApplicationRepository;
import ASSRONE.backend.repository.UserInfoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MembershipApplicationServiceTest {

    @Mock
    private MembershipApplicationRepository repository;

    @Mock
    private MembershipApplicationMapper mapper;

    @Mock
    private MembershipEmailService membershipEmailService;

    @Mock
    private UserInfoRepository userInfoRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private MembershipApplicationService service() {
        return new MembershipApplicationService(repository, mapper, membershipEmailService, userInfoRepository, passwordEncoder);
    }

    @Test
    void acceptAvecUnIdInexistantLeveResourceNotFound() {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().accept(42L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Demande introuvable : 42");
    }

    @Test
    void rejectAvecUnIdInexistantLeveResourceNotFound() {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().reject(42L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Demande introuvable : 42");
    }
}
