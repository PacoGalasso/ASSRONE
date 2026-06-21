package ASSRONE.backend.mapper;

import ASSRONE.backend.dto.UserDto;
import ASSRONE.backend.model.User;
import org.mapstruct.Mapper;


@Mapper(componentModel = "spring")

public interface UserMapper {

    UserDto toDto(User user);

    User toEntity(UserDto userDto);
}
