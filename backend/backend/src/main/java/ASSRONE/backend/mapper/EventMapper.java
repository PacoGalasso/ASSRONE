// mapper/EventMapper.java — remplace le fichier entier
package ASSRONE.backend.mapper;

import ASSRONE.backend.dto.CreateEventRequest;
import ASSRONE.backend.dto.EventDto;
import ASSRONE.backend.model.Event;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EventMapper {

    EventDto toDto(Event event);

    Event toEntity(EventDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "currentParticipants", constant = "0")
    Event fromCreateRequest(CreateEventRequest request);
}