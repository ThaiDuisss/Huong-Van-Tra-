package org.hvtra.promotionservice.mapper;

import org.hvtra.promotionservice.dto.request.promotiontarget.PromotionTargetCreateRequest;
import org.hvtra.promotionservice.dto.request.promotiontarget.PromotionTargetUpdateRequest;
import org.hvtra.promotionservice.dto.response.PromotionTargetResponse;
import org.hvtra.promotionservice.entity.PromotionTarget;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface PromotionTargetMapper {

    @Mapping(target = "promotion", ignore = true)
    PromotionTarget toEntity(PromotionTargetCreateRequest request);

    @Mapping(target = "promotionId", source = "promotion.id")
    PromotionTargetResponse toResponse(PromotionTarget target);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "promotion", ignore = true)
    void updateEntityFromRequest(
            PromotionTargetUpdateRequest request,
            @MappingTarget PromotionTarget target
    );
}