package ASSRONE.backend.mapper;

import ASSRONE.backend.dto.CreateMembershipApplicationRequest;
import ASSRONE.backend.dto.MembershipApplicationDto;
import ASSRONE.backend.model.MembershipApplication;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MembershipApplicationMapper {

    MembershipApplicationDto toDto(MembershipApplication entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "submittedAt", ignore = true)
    MembershipApplication fromCreateRequest(CreateMembershipApplicationRequest request);
}
