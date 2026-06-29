package org.hvtra.promotionservice.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.exception.ResourceNotFoundException;
import org.hvtra.promotionservice.dto.response.CouponUsageResponse;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.entity.coupon.Coupon;
import org.hvtra.promotionservice.entity.coupon.CouponUsage;
import org.hvtra.promotionservice.enums.coupon.CouponUsageStatus;
import org.hvtra.promotionservice.mapper.CouponUsageMapper;
import org.hvtra.promotionservice.repository.coupon.CouponRepository;
import org.hvtra.promotionservice.repository.coupon.CouponUsageRepository;
import org.hvtra.promotionservice.service.CouponUsageService;
import org.hvtra.promotionservice.validation.validator.CouponUsageValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hvtra.promotionservice.constant.coupon.CouponErrorMessage.COUPON_NOT_FOUND_WITH_ID;
import static org.hvtra.promotionservice.constant.couponusage.CouponUsageErrorMessage.COUPON_USAGE_NOT_FOUND_WITH_ID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CouponUsageServiceImpl implements CouponUsageService {

    CouponUsageRepository couponUsageRepository;
    CouponRepository couponRepository;
    CouponUsageMapper couponUsageMapper;
    CouponUsageValidator couponUsageValidator;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CouponUsageResponse> getAllCouponUsages(int page, int size) {
        Pageable pageable = createPageable(page, size);

        Page<CouponUsageResponse> usagePage = couponUsageRepository.findAllByIsDeletedFalse(pageable)
                .map(couponUsageMapper::toResponse);

        return PageResponse.from(usagePage);
    }

    @Override
    @Transactional(readOnly = true)
    public CouponUsageResponse getCouponUsageById(UUID usageId) {
        CouponUsage usage = getExistingUsage(usageId);

        return couponUsageMapper.toResponse(usage);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CouponUsageResponse> getUsagesByCoupon(UUID couponId, int page, int size) {
        getExistingCoupon(couponId);

        Pageable pageable = createPageable(page, size);

        Page<CouponUsageResponse> usagePage =
                couponUsageRepository.findAllByCouponIdAndIsDeletedFalse(couponId, pageable)
                        .map(couponUsageMapper::toResponse);

        return PageResponse.from(usagePage);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CouponUsageResponse> getUsagesByOrder(UUID orderId, int page, int size) {
        Pageable pageable = createPageable(page, size);

        Page<CouponUsageResponse> usagePage =
                couponUsageRepository.findAllByOrderIdAndIsDeletedFalse(orderId, pageable)
                        .map(couponUsageMapper::toResponse);

        return PageResponse.from(usagePage);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CouponUsageResponse> getUsagesByCustomer(UUID customerId, int page, int size) {
        Pageable pageable = createPageable(page, size);

        Page<CouponUsageResponse> usagePage =
                couponUsageRepository.findAllByCustomerIdAndIsDeletedFalse(customerId, pageable)
                        .map(couponUsageMapper::toResponse);

        return PageResponse.from(usagePage);
    }

    @Override
    @Transactional
    public CouponUsageResponse releaseUsage(UUID usageId) {
        CouponUsage usage = getExistingUsage(usageId);

        couponUsageValidator.validateBeforeRelease(usage);

        usage.setStatus(CouponUsageStatus.RELEASED);
        usage.setReleasedAt(LocalDateTime.now());

        CouponUsage updatedUsage = couponUsageRepository.save(usage);

        return couponUsageMapper.toResponse(updatedUsage);
    }

    @Override
    @Transactional
    public CouponUsageResponse cancelUsage(UUID usageId) {
        CouponUsage usage = getExistingUsage(usageId);

        couponUsageValidator.validateBeforeCancel(usage);

        usage.setStatus(CouponUsageStatus.CANCELLED);

        CouponUsage updatedUsage = couponUsageRepository.save(usage);

        return couponUsageMapper.toResponse(updatedUsage);
    }

    @Override
    @Transactional
    public CouponUsageResponse expireUsage(UUID usageId) {
        CouponUsage usage = getExistingUsage(usageId);

        couponUsageValidator.validateBeforeExpire(usage);

        usage.setStatus(CouponUsageStatus.EXPIRED);

        CouponUsage updatedUsage = couponUsageRepository.save(usage);

        return couponUsageMapper.toResponse(updatedUsage);
    }

    private CouponUsage getExistingUsage(UUID usageId) {
        return couponUsageRepository.findByIdAndIsDeletedFalse(usageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        COUPON_USAGE_NOT_FOUND_WITH_ID.formatted(usageId)
                ));
    }

    private Coupon getExistingCoupon(UUID couponId) {
        return couponRepository.findByIdAndIsDeletedFalse(couponId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        COUPON_NOT_FOUND_WITH_ID.formatted(couponId)
                ));
    }

    private Pageable createPageable(int page, int size) {
        return PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
    }
}