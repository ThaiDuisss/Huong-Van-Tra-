package org.hvtra.promotionservice.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.common_core.exception.BusinessException;
import org.hvtra.commonservice.common_core.exception.ResourceNotFoundException;
import org.hvtra.promotionservice.dto.request.couponusage.CouponUsageFilterRequest;
import org.hvtra.promotionservice.dto.request.couponusage.CouponUsageReserveRequest;
import org.hvtra.promotionservice.dto.response.CouponUsageResponse;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.entity.coupon.Coupon;
import org.hvtra.promotionservice.entity.coupon.CouponUsage;
import org.hvtra.promotionservice.enums.coupon.CouponUsageStatus;
import org.hvtra.promotionservice.mapper.CouponUsageMapper;
import org.hvtra.promotionservice.repository.coupon.CouponRepository;
import org.hvtra.promotionservice.repository.coupon.CouponUsageRepository;
import org.hvtra.promotionservice.service.CouponUsageService;
import org.hvtra.promotionservice.validation.validator.CouponValidator;
import org.hvtra.promotionservice.validation.validator.CouponUsageValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
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
    CouponValidator couponValidator;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CouponUsageResponse> getAllCouponUsages(int page, int size) {
        return getCouponUsages(new CouponUsageFilterRequest(), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CouponUsageResponse> getCouponUsages(CouponUsageFilterRequest request, int page, int size) {
        CouponUsageFilterRequest filter = request == null ? new CouponUsageFilterRequest() : request;
        validateReservedRange(filter.getReservedFrom(), filter.getReservedTo());

        Pageable pageable = createPageable(page, size);

        Page<CouponUsageResponse> usagePage = couponUsageRepository.searchCouponUsages(
                        filter.getCouponId(),
                        normalizeKeyword(filter.getCouponCode()),
                        filter.getOrderId(),
                        filter.getCustomerId(),
                        filter.getStatus(),
                        filter.getReservedFrom(),
                        filter.getReservedTo(),
                        pageable
                )
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

        return getCouponUsages(CouponUsageFilterRequest.builder()
                .couponId(couponId)
                .build(), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CouponUsageResponse> getUsagesByOrder(UUID orderId, int page, int size) {
        return getCouponUsages(CouponUsageFilterRequest.builder()
                .orderId(orderId)
                .build(), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CouponUsageResponse> getUsagesByCustomer(UUID customerId, int page, int size) {
        return getCouponUsages(CouponUsageFilterRequest.builder()
                .customerId(customerId)
                .build(), page, size);
    }

    @Override
    @Transactional
    public CouponUsageResponse reserveCoupon(CouponUsageReserveRequest request) {
        String couponCode = request.getCouponCode().trim();
        Coupon coupon = couponRepository.findByCodeAndIsDeletedFalseForUpdate(couponCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Coupon not found with code: " + couponCode
                ));

        return couponUsageRepository
                .findByCouponIdAndOrderIdAndStatusAndIsDeletedFalse(
                        coupon.getId(),
                        request.getOrderId(),
                        CouponUsageStatus.RESERVED
                )
                .map(couponUsageMapper::toResponse)
                .orElseGet(() -> {
                    couponValidator.validateBeforeReserve(coupon, request);
                    validatePerCustomerLimit(coupon, request);
                    return createReservation(coupon, request);
                });
    }

    @Override
    @Transactional
    public CouponUsageResponse applyUsage(UUID usageId) {
        CouponUsage usage = getExistingUsage(usageId);

        if (usage.getStatus() == CouponUsageStatus.APPLIED) {
            return couponUsageMapper.toResponse(usage);
        }

        couponUsageValidator.validateBeforeApply(usage);

        usage.setStatus(CouponUsageStatus.APPLIED);
        usage.setUsedAt(LocalDateTime.now());

        CouponUsage updatedUsage = couponUsageRepository.save(usage);

        return couponUsageMapper.toResponse(updatedUsage);
    }

    @Override
    @Transactional
    public CouponUsageResponse releaseUsage(UUID usageId) {
        CouponUsage usage = getExistingUsage(usageId);

        couponUsageValidator.validateBeforeRelease(usage);

        restoreReservedCouponQuota(usage);

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

        restoreReservedCouponQuota(usage);

        usage.setStatus(CouponUsageStatus.CANCELLED);

        CouponUsage updatedUsage = couponUsageRepository.save(usage);

        return couponUsageMapper.toResponse(updatedUsage);
    }

    @Override
    @Transactional
    public CouponUsageResponse expireUsage(UUID usageId) {
        CouponUsage usage = getExistingUsage(usageId);

        couponUsageValidator.validateBeforeExpire(usage);

        restoreReservedCouponQuota(usage);

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

    private CouponUsageResponse createReservation(Coupon coupon, CouponUsageReserveRequest request) {
        coupon.setUsedCount((coupon.getUsedCount() == null ? 0 : coupon.getUsedCount()) + 1);
        couponRepository.save(coupon);

        LocalDateTime now = LocalDateTime.now();
        CouponUsage usage = couponUsageMapper.toEntity(request);
        usage.setCoupon(coupon);
        usage.setStatus(CouponUsageStatus.RESERVED);
        usage.setReservedAt(now);
        usage.setReservationExpiresAt(now.plusMinutes(15));
        usage.setReservationToken(generateReservationToken());

        CouponUsage savedUsage = couponUsageRepository.save(usage);

        return couponUsageMapper.toResponse(savedUsage);
    }

    private void validatePerCustomerLimit(Coupon coupon, CouponUsageReserveRequest request) {
        int perCustomerLimit = coupon.getPerCustomerLimit() == null ? 1 : coupon.getPerCustomerLimit();
        long customerUsageCount = couponUsageRepository.countByCouponIdAndCustomerIdAndStatusInAndIsDeletedFalse(
                coupon.getId(),
                request.getCustomerId(),
                List.of(CouponUsageStatus.RESERVED, CouponUsageStatus.APPLIED)
        );

        if (customerUsageCount >= perCustomerLimit) {
            throw new BusinessException("Customer has reached coupon usage limit");
        }
    }

    private void restoreReservedCouponQuota(CouponUsage usage) {
        if (usage.getStatus() != CouponUsageStatus.RESERVED || usage.getCoupon() == null) {
            return;
        }

        Coupon coupon = usage.getCoupon();
        int usedCount = coupon.getUsedCount() == null ? 0 : coupon.getUsedCount();
        coupon.setUsedCount(Math.max(0, usedCount - 1));
        couponRepository.save(coupon);
    }

    private String generateReservationToken() {
        return "RES-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim();
    }

    private void validateReservedRange(LocalDateTime reservedFrom, LocalDateTime reservedTo) {
        if (reservedFrom != null && reservedTo != null && reservedFrom.isAfter(reservedTo)) {
            throw new BusinessException("Reserved from must be before or equal to reserved to");
        }
    }
}
