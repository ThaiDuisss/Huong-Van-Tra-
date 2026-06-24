package org.hvtra.promotionservice.mapper;

import org.hvtra.promotionservice.dto.request.event.EventCreateRequest;
import org.hvtra.promotionservice.dto.request.event.EventUpdateRequest;
import org.hvtra.promotionservice.dto.response.EventResponse;
import org.hvtra.promotionservice.entity.Event;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring")
public interface EventMapper {

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    Event toEntity(EventCreateRequest request);

    EventResponse toResponse(Event event);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    void updateEntityFromRequest(EventUpdateRequest request, @MappingTarget Event event);

}
