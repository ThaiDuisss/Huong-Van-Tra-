package org.hvtra.promotionservice.mapper;

import org.hvtra.promotionservice.dto.request.couponusage.CouponUsageReserveRequest;
import org.hvtra.promotionservice.dto.response.CouponUsageResponse;
import org.hvtra.promotionservice.entity.coupon.CouponUsage;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring")
public interface CouponUsageMapper {

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    CouponUsage toEntity(CouponUsageReserveRequest request);

    @Mapping(target = "couponId", source = "coupon.id")
    @Mapping(target = "couponCode", source = "coupon.code")
    CouponUsageResponse toResponse(CouponUsage usage);
}