package ASSRONE.backend.mapper;

import ASSRONE.backend.dto.DocumentDto;
import ASSRONE.backend.model.Document;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DocumentMapper {
    DocumentDto toDto(Document document);
}
