//package org.hvtra.promotionservice.service.impl;
//
//import lombok.AccessLevel;
//import lombok.RequiredArgsConstructor;
//import lombok.experimental.FieldDefaults;
//import org.hvtra.commonservice.exception.ResourceNotFoundException;
//import org.hvtra.promotionservice.dto.request.couponusage.CouponUsageReserveRequest;
//import org.hvtra.promotionservice.dto.response.CouponUsageResponse;
//import org.hvtra.promotionservice.dto.response.PageResponse;
//import org.hvtra.promotionservice.entity.coupon.Coupon;
//import org.hvtra.promotionservice.entity.coupon.CouponUsage;
//import org.hvtra.promotionservice.enums.coupon.CouponStatus;
//import org.hvtra.promotionservice.enums.coupon.CouponUsageStatus;
//import org.hvtra.promotionservice.mapper.CouponUsageMapper;
//import org.hvtra.promotionservice.repository.coupon.CouponRepository;
//import org.hvtra.promotionservice.repository.coupon.CouponUsageRepository;
//import org.hvtra.promotionservice.service.CouponUsageService;
//import org.springframework.data.domain.*;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.LocalDateTime;
//import java.util.UUID;
//
//import static org.hvtra.promotionservice.constant.couponusage.CouponUsageErrorMessage.*;
//
//@Service
//@RequiredArgsConstructor
//@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
//public class CouponUsageServiceImpl implements CouponUsageService {
//
//    private static final int RESERVATION_EXPIRE_MINUTES = 15;
//
//    CouponRepository couponRepository;
//    CouponUsageRepository couponUsageRepository;
//    CouponUsageMapper couponUsageMapper;
//
//    @Override
//    @Transactional
//    public CouponUsageResponse reserveCoupon(CouponUsageReserveRequest request) {
////        Coupon coupon = couponRepository.findByCodeAndIsDeletedFalse(request.getCouponCode())
////                .orElseThrow(() -> new ResourceNotFoundException(
////                        COUPON_NOT_FOUND_WITH_CODE.formatted(request.getCouponCode())
////                ));
//
//        Coupon coupon = new Coupon();
//
//        validateCouponCanBeReserved(coupon);
//
//        if (couponUsageRepository.existsByCouponIdAndOrderIdAndIsDeletedFalse(
//                coupon.getId(),
//                request.getOrderId()
//        )) {
//            throw new IllegalArgumentException(COUPON_ALREADY_RESERVED_FOR_ORDER);
//        }
//
//        CouponUsage usage = couponUsageMapper.toEntity(request);
//        usage.setCoupon(coupon);
//        usage.setStatus(CouponUsageStatus.RESERVED);
//        usage.setReservationToken(UUID.randomUUID().toString());
//        usage.setReservedAt(LocalDateTime.now());
//        usage.setReservationExpiresAt(LocalDateTime.now().plusMinutes(RESERVATION_EXPIRE_MINUTES));
//
//        CouponUsage savedUsage = couponUsageRepository.save(usage);
//
//        return couponUsageMapper.toResponse(savedUsage);
//    }
//
//    @Override
//    @Transactional
//    public CouponUsageResponse confirmCouponUsage(UUID usageId) {
//        CouponUsage usage = getUsageOrThrow(usageId);
//
//        if (usage.getStatus() != CouponUsageStatus.RESERVED) {
//            throw new IllegalStateException(COUPON_USAGE_NOT_RESERVED);
//        }
//
//        Coupon coupon = usage.getCoupon();
//        validateCouponCanBeReserved(coupon);
//
//        usage.setStatus(CouponUsageStatus.APPLIED);
//        usage.setUsedAt(LocalDateTime.now());
//
//        coupon.setUsedCount(coupon.getUsedCount() + 1);
//
//        CouponUsage updatedUsage = couponUsageRepository.save(usage);
//
//        return couponUsageMapper.toResponse(updatedUsage);
//    }
//
//    @Override
//    @Transactional
//    public CouponUsageResponse releaseCouponUsage(UUID usageId) {
//        CouponUsage usage = getUsageOrThrow(usageId);
//
//        if (usage.getStatus() != CouponUsageStatus.RESERVED) {
//            throw new IllegalStateException(COUPON_USAGE_NOT_RESERVED);
//        }
//
//        usage.setStatus(CouponUsageStatus.RELEASED);
//        usage.setReleasedAt(LocalDateTime.now());
//
//        CouponUsage updatedUsage = couponUsageRepository.save(usage);
//
//        return couponUsageMapper.toResponse(updatedUsage);
//    }
//
//    @Override
//    @Transactional(readOnly = true)
//    public CouponUsageResponse getCouponUsageById(UUID usageId) {
//        return couponUsageMapper.toResponse(getUsageOrThrow(usageId));
//    }
//
//    @Override
//    @Transactional(readOnly = true)
//    public PageResponse<CouponUsageResponse> getCouponUsagesByCoupon(
//            UUID couponId,
//            int page,
//            int size
//    ) {
//        Pageable pageable = createPageable(page, size);
//
//        Page<CouponUsageResponse> usagePage = couponUsageRepository
//                .findAllByCouponIdAndIsDeletedFalse(couponId, pageable)
//                .map(couponUsageMapper::toResponse);
//
//        return PageResponse.from(usagePage);
//    }
//
//    @Override
//    @Transactional(readOnly = true)
//    public PageResponse<CouponUsageResponse> getCouponUsagesByCustomer(
//            UUID customerId,
//            int page,
//            int size
//    ) {
//        Pageable pageable = createPageable(page, size);
//
//        Page<CouponUsageResponse> usagePage = couponUsageRepository
//                .findAllByCustomerIdAndIsDeletedFalse(customerId, pageable)
//                .map(couponUsageMapper::toResponse);
//
//        return PageResponse.from(usagePage);
//    }
//
//    private CouponUsage getUsageOrThrow(UUID usageId) {
//        return couponUsageRepository.findByIdAndIsDeletedFalse(usageId)
//                .orElseThrow(() -> new ResourceNotFoundException(
//                        COUPON_USAGE_NOT_FOUND_WITH_ID.formatted(usageId)
//                ));
//    }
//
//    private void validateCouponCanBeReserved(Coupon coupon) {
//        if (coupon.getStatus() != CouponStatus.ACTIVE) {
//            throw new IllegalStateException(COUPON_NOT_ACTIVE);
//        }
//
//        if (coupon.getExpiresAt() != null && coupon.getExpiresAt().isBefore(LocalDateTime.now())) {
//            throw new IllegalStateException(COUPON_EXPIRED);
//        }
//
//        if (coupon.getUsedCount() >= coupon.getUsageLimit()) {
//            throw new IllegalStateException(COUPON_USAGE_LIMIT_EXCEEDED);
//        }
//    }
//
//    private Pageable createPageable(int page, int size) {
//        return PageRequest.of(
//                page,
//                size,
//                Sort.by(Sort.Direction.DESC, "createdAt")
//        );
//    }
//}