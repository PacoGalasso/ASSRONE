package ASSRONE.backend.mapper;

import ASSRONE.backend.dto.UserDto;
import ASSRONE.backend.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserDto toDto(User user);

    // UserDto never carries these fields by design (it is a public-facing
    // read DTO), so they must never be silently populated from one — a
    // password, lockout state, or internal avatar filename set this way
    // would bypass PasswordEncoder/LoginAttemptService entirely. Each is
    // ignored explicitly rather than suppressing the unmapped-target warning
    // globally, so a genuinely new unmapped field still surfaces a warning.
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "avatarFilename", ignore = true)
    @Mapping(target = "lastLogin", ignore = true)
    @Mapping(target = "failedLoginAttempts", ignore = true)
    @Mapping(target = "lockedUntil", ignore = true)
    User toEntity(UserDto userDto);
}
