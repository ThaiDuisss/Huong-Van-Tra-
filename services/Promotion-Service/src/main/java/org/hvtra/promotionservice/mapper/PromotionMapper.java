package org.hvtra.promotionservice.mapper;

import org.hvtra.promotionservice.dto.request.promotion.PromotionCreateRequest;
import org.hvtra.promotionservice.dto.request.promotion.PromotionUpdateRequest;
import org.hvtra.promotionservice.dto.response.PromotionResponse;
import org.hvtra.promotionservice.entity.Promotion;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring")
public interface PromotionMapper {

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    Promotion toEntity(PromotionCreateRequest request);

    PromotionResponse toResponse(Promotion promotion);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    void updateEntityFromRequest(PromotionUpdateRequest request, @MappingTarget Promotion promotion);
}
