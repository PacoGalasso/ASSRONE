package ASSRONE.backend.mapper;

import ASSRONE.backend.dto.UserDto;
import ASSRONE.backend.model.User;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private final UserMapper mapper = Mappers.getMapper(UserMapper.class);

    @Test
    void toEntityNeLaisseJamaisUnDtoPositionnerLesChampsSensibles() {
        UserDto dto = new UserDto();
        dto.setId(1L);
        dto.setUsername("jean.dupont");
        dto.setEmail("jean.dupont@assrone.ch");
        dto.setFirstName("Jean");
        dto.setLastName("Dupont");
        dto.setRole("ADMIN");
        dto.setIsActive(true);
        dto.setCreatedAt(LocalDateTime.now());
        dto.setUpdatedAt(LocalDateTime.now());

        User entity = mapper.toEntity(dto);

        assertThat(entity.getPassword()).isNull();
        assertThat(entity.getAvatarFilename()).isNull();
        assertThat(entity.getLastLogin()).isNull();
        assertThat(entity.getFailedLoginAttempts()).isZero();
        assertThat(entity.getLockedUntil()).isNull();
    }

    @Test
    void toEntityMappeCorrectementLesChampsNonSensibles() {
        LocalDateTime createdAt = LocalDateTime.now().minusDays(30);
        UserDto dto = new UserDto();
        dto.setId(1L);
        dto.setUsername("jean.dupont");
        dto.setEmail("jean.dupont@assrone.ch");
        dto.setFirstName("Jean");
        dto.setLastName("Dupont");
        dto.setRole("ADMIN");
        dto.setIsActive(true);
        dto.setCreatedAt(createdAt);

        User entity = mapper.toEntity(dto);

        assertThat(entity.getUsername()).isEqualTo("jean.dupont");
        assertThat(entity.getEmail()).isEqualTo("jean.dupont@assrone.ch");
        assertThat(entity.getRole()).isEqualTo("ADMIN");
    }
}
