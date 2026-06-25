package org.hvtra.promotionservice.mapper;

import org.hvtra.promotionservice.dto.request.promotioncondition.PromotionConditionCreateRequest;
import org.hvtra.promotionservice.dto.request.promotioncondition.PromotionConditionUpdateRequest;
import org.hvtra.promotionservice.dto.response.PromotionConditionResponse;
import org.hvtra.promotionservice.entity.PromotionCondition;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring")

public interface PromotionConditionMapper {

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    PromotionCondition toEntity(PromotionConditionCreateRequest request);

    PromotionConditionResponse toResponse(PromotionCondition promotionCondition);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    void updateEntityFromRequest(
            PromotionConditionUpdateRequest request,
            @MappingTarget PromotionCondition promotionCondition
    );

}
