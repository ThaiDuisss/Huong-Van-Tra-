package org.hvtra.promotionservice.mapper;

import org.hvtra.promotionservice.dto.request.promotionbenefit.PromotionBenefitCreateRequest;
import org.hvtra.promotionservice.dto.request.promotionbenefit.PromotionBenefitUpdateRequest;
import org.hvtra.promotionservice.dto.response.PromotionBenefitResponse;
import org.hvtra.promotionservice.entity.PromotionBenefit;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface PromotionBenefitMapper {

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    PromotionBenefit toEntity(PromotionBenefitCreateRequest request);

    @Mapping(target = "promotionId", source = "promotion.id")
    PromotionBenefitResponse toResponse(PromotionBenefit promotionBenefit);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    void updateEntityFromRequest(
            PromotionBenefitUpdateRequest request,
            @MappingTarget PromotionBenefit promotionBenefit
    );

}
