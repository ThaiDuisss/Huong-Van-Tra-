package org.hvtra.promotionservice.mapper;

import org.hvtra.promotionservice.dto.request.couponusage.CouponUsageReserveRequest;
import org.hvtra.promotionservice.dto.response.CouponUsageResponse;
import org.hvtra.promotionservice.entity.coupon.CouponUsage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CouponUsageMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "coupon", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "reservationToken", ignore = true)
    @Mapping(target = "reservedAt", ignore = true)
    @Mapping(target = "reservationExpiresAt", ignore = true)
    @Mapping(target = "usedAt", ignore = true)
    @Mapping(target = "releasedAt", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    CouponUsage toEntity(CouponUsageReserveRequest request);

    @Mapping(target = "couponId", source = "coupon.id")
    @Mapping(target = "couponCode", source = "coupon.code")
    CouponUsageResponse toResponse(CouponUsage usage);
}