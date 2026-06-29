package org.hvtra.promotionservice.mapper;

import org.hvtra.promotionservice.dto.request.promotion.PromotionCreateRequest;
import org.hvtra.promotionservice.dto.request.promotion.PromotionUpdateRequest;
import org.hvtra.promotionservice.dto.response.PromotionResponse;
import org.hvtra.promotionservice.entity.Promotion;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface PromotionMapper {

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    Promotion toEntity(PromotionCreateRequest request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    PromotionResponse toResponse(Promotion promotion);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    void updateEntityFromRequest(PromotionUpdateRequest request, @MappingTarget Promotion promotion);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "targets", ignore = true)
    @Mapping(target = "conditions", ignore = true)
    @Mapping(target = "benefits", ignore = true)
    @Mapping(target = "coupons", ignore = true)
    @Mapping(target = "name", expression = "java(source.getName() + \" - Copy\")")
    @Mapping(target = "status", constant = "DRAFT")
    Promotion duplicateFrom(Promotion source);
}
