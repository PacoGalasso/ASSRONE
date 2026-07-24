package ASSRONE.backend.mapper;


import ASSRONE.backend.dto.UserProfileDto;
import ASSRONE.backend.model.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserProfileMapper {
    UserProfileDto toDto(User user);
}
