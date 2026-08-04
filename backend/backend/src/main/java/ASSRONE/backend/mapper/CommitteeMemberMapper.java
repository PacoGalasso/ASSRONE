package ASSRONE.backend.mapper;

import ASSRONE.backend.dto.CommitteeMemberDto;
import ASSRONE.backend.dto.CreateCommitteeMemberRequest;
import ASSRONE.backend.dto.UpdateCommitteeMemberRequest;
import ASSRONE.backend.model.CommitteeMember;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CommitteeMemberMapper {

    @Mapping(target = "hasPhoto", expression = "java(member.getPhotoFilename() != null)")
    CommitteeMemberDto toDto(CommitteeMember member);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "photoFilename", ignore = true)
    CommitteeMember fromCreateRequest(CreateCommitteeMemberRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "photoFilename", ignore = true)
    void updateEntityFromRequest(UpdateCommitteeMemberRequest request, @MappingTarget CommitteeMember member);
}
