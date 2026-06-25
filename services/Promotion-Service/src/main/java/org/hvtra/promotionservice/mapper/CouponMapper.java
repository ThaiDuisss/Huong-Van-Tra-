package org.hvtra.promotionservice.mapper;

import org.hvtra.promotionservice.dto.request.coupon.CouponCreateRequest;
import org.hvtra.promotionservice.dto.request.coupon.CouponUpdateRequest;
import org.hvtra.promotionservice.dto.response.CouponResponse;
import org.hvtra.promotionservice.entity.coupon.Coupon;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring")
public interface CouponMapper {

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    Coupon toEntity(CouponCreateRequest request);

    CouponResponse toResponse(Coupon coupon);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    void updateEntityFromRequest(CouponUpdateRequest request, @MappingTarget Coupon coupon);

}
