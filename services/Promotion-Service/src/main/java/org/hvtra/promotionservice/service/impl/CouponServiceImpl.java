package org.hvtra.promotionservice.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.exception.ResourceNotFoundException;
import org.hvtra.promotionservice.dto.request.coupon.*;
import org.hvtra.promotionservice.dto.response.CouponResponse;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.entity.Promotion;
import org.hvtra.promotionservice.entity.PromotionBenefit;
import org.hvtra.promotionservice.entity.coupon.Coupon;
import org.hvtra.promotionservice.enums.coupon.CouponScope;
import org.hvtra.promotionservice.enums.coupon.CouponStatus;
import org.hvtra.promotionservice.mapper.CouponMapper;
import org.hvtra.promotionservice.repository.PromotionRepository;
import org.hvtra.promotionservice.repository.coupon.CouponRepository;
import org.hvtra.promotionservice.service.CouponService;
import org.hvtra.promotionservice.validation.validator.CouponValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hvtra.promotionservice.constant.coupon.CouponErrorMessage.COUPON_NOT_FOUND_WITH_ID;
import static org.hvtra.promotionservice.constant.promotion.PromotionErrorMessage.PROMOTION_NOT_FOUND_WITH_ID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CouponServiceImpl implements CouponService {

    CouponRepository couponRepository;
    PromotionRepository promotionRepository;
    CouponMapper couponMapper;
    CouponValidator couponValidator;

    @Override
    @Transactional
    public CouponResponse createCoupon(CouponCreateRequest request) {
        couponValidator.validateCreateRequest(request);

        return createCouponInternal(request.getPromotionId(), request);
    }

    @Override
    @Transactional
    public CouponResponse createCouponByPromotion(UUID promotionId, CouponCreateRequest request) {
        return createCouponInternal(promotionId, request);
    }

    @Override
    @Transactional(readOnly = true)
    public CouponResponse getCouponById(UUID couponId) {
        Coupon coupon = getExistingCoupon(couponId);

        return couponMapper.toResponse(coupon);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CouponResponse> getAllCoupons(int page, int size) {
        Pageable pageable = createPageable(page, size);

        Page<CouponResponse> couponPage = couponRepository.findAllByIsDeletedFalse(pageable)
                .map(couponMapper::toResponse);

        return PageResponse.from(couponPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CouponResponse> getCouponsByPromotion(UUID promotionId, int page, int size) {
        getExistingPromotion(promotionId);

        Pageable pageable = createPageable(page, size);

        Page<CouponResponse> couponPage = couponRepository
                .findAllByPromotionIdAndIsDeletedFalse(promotionId, pageable)
                .map(couponMapper::toResponse);

        return PageResponse.from(couponPage);
    }

    @Override
    @Transactional
    public CouponResponse updateCoupon(UUID couponId, CouponUpdateRequest request) {
        Coupon coupon = getExistingCoupon(couponId);

        couponValidator.validateBeforeUpdate(coupon, request);

        couponMapper.updateEntityFromRequest(request, coupon);

        Coupon updatedCoupon = couponRepository.save(coupon);

        return couponMapper.toResponse(updatedCoupon);
    }

    @Override
    @Transactional
    public CouponResponse updateCouponStatus(UUID couponId, CouponStatusUpdateRequest request) {
        Coupon coupon = getExistingCoupon(couponId);

        couponValidator.validateStatusTransition(coupon, request);

        coupon.setStatus(request.getStatus());

        Coupon updatedCoupon = couponRepository.save(coupon);

        return couponMapper.toResponse(updatedCoupon);
    }

    @Override
    @Transactional
    public void deleteCoupon(UUID couponId) {
        Coupon coupon = getExistingCoupon(couponId);

        couponValidator.validateBeforeDelete(coupon);

        coupon.setIsDeleted(true);

        couponRepository.save(coupon);
    }

    private CouponResponse createCouponInternal(UUID promotionId, CouponCreateRequest request) {
        Promotion promotion = getExistingPromotion(promotionId);

        couponValidator.validateBeforeCreate(promotion, request);

        Coupon coupon = couponMapper.toEntity(request);
        coupon.setPromotion(promotion);

        if (coupon.getUsageLimit() == null) {
            coupon.setUsageLimit(1);
        }

        if (coupon.getUsedCount() == null) {
            coupon.setUsedCount(0);
        }

        applySnapshot(coupon, promotion);

        Coupon savedCoupon = couponRepository.save(coupon);

        return couponMapper.toResponse(savedCoupon);
    }

    private void applySnapshot(Coupon coupon, Promotion promotion) {
        coupon.setSnapshotPromotionName(promotion.getName());

        Optional<PromotionBenefit> firstBenefit = promotion.getBenefits()
                .stream()
                .filter(benefit -> !Boolean.TRUE.equals(benefit.getIsDeleted()))
                .findFirst();

        firstBenefit.ifPresent(benefit -> {
            coupon.setSnapshotBenefitType(benefit.getBenefitType());
            coupon.setSnapshotDiscountValue(benefit.getValue());
            coupon.setSnapshotMaxDiscount(benefit.getMaxDiscount());
        });
    }

    private Coupon getExistingCoupon(UUID couponId) {
        return couponRepository.findByIdAndIsDeletedFalse(couponId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        COUPON_NOT_FOUND_WITH_ID.formatted(couponId)
                ));
    }

    private Promotion getExistingPromotion(UUID promotionId) {
        return promotionRepository.findByIdAndIsDeletedFalse(promotionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        PROMOTION_NOT_FOUND_WITH_ID.formatted(promotionId)
                ));
    }

    private Pageable createPageable(int page, int size) {
        return PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
    }

    @Override
    @Transactional
    public CouponResponse activateCoupon(UUID couponId) {
        Coupon coupon = getExistingCoupon(couponId);

        couponValidator.validateBeforeActivate(coupon);

        coupon.setStatus(CouponStatus.ACTIVE);

        Coupon updatedCoupon = couponRepository.save(coupon);

        return couponMapper.toResponse(updatedCoupon);
    }

    @Override
    @Transactional
    public CouponResponse pauseCoupon(UUID couponId) {
        Coupon coupon = getExistingCoupon(couponId);

        couponValidator.validateBeforePause(coupon);

        coupon.setStatus(CouponStatus.PAUSED);

        Coupon updatedCoupon = couponRepository.save(coupon);

        return couponMapper.toResponse(updatedCoupon);
    }

    @Override
    @Transactional
    public CouponResponse cancelCoupon(UUID couponId) {
        Coupon coupon = getExistingCoupon(couponId);

        couponValidator.validateBeforeCancel(coupon);

        coupon.setStatus(CouponStatus.CANCELLED);

        Coupon updatedCoupon = couponRepository.save(coupon);

        return couponMapper.toResponse(updatedCoupon);
    }

    @Override
    @Transactional
    public CouponResponse archiveCoupon(UUID couponId) {
        Coupon coupon = getExistingCoupon(couponId);

        couponValidator.validateBeforeArchive(coupon);

        coupon.setStatus(CouponStatus.ARCHIVED);

        Coupon updatedCoupon = couponRepository.save(coupon);

        return couponMapper.toResponse(updatedCoupon);
    }

    @Override
    @Transactional
    public CouponResponse generatePublicCoupon(UUID promotionId, CouponGeneratePublicRequest request) {
        Promotion promotion = getExistingPromotion(promotionId);

        couponValidator.validateGeneratePublic(request);

        Coupon coupon = Coupon.builder()
                .promotion(promotion)
                .scope(CouponScope.PUBLIC)
                .code(generateUniqueCouponCode(request.getPrefix()))
                .issueReason(request.getIssueReason())
                .issuedAt(LocalDateTime.now())
                .usageLimit(request.getUsageLimit() == null ? 1 : request.getUsageLimit())
                .usedCount(0)
                .expiresAt(request.getExpiresAt())
                .status(CouponStatus.DRAFT)
                .build();

        applySnapshot(coupon, promotion);

        Coupon savedCoupon = couponRepository.save(coupon);

        return couponMapper.toResponse(savedCoupon);
    }

    @Override
    @Transactional
    public CouponResponse generatePersonalCoupon(UUID promotionId, CouponGeneratePersonalRequest request) {
        Promotion promotion = getExistingPromotion(promotionId);

        couponValidator.validateGeneratePersonal(request);

        Coupon coupon = Coupon.builder()
                .promotion(promotion)
                .scope(CouponScope.PERSONAL)
                .customerId(request.getCustomerId())
                .code(generateUniqueCouponCode(request.getPrefix()))
                .issueReason(request.getIssueReason())
                .sourceOrderId(request.getSourceOrderId())
                .issuedAt(LocalDateTime.now())
                .usageLimit(request.getUsageLimit() == null ? 1 : request.getUsageLimit())
                .usedCount(0)
                .expiresAt(request.getExpiresAt())
                .status(CouponStatus.DRAFT)
                .build();

        applySnapshot(coupon, promotion);

        Coupon savedCoupon = couponRepository.save(coupon);

        return couponMapper.toResponse(savedCoupon);
    }

    @Override
    @Transactional
    public List<CouponResponse> bulkGenerateCoupons(UUID promotionId, CouponBulkGenerateRequest request) {
        Promotion promotion = getExistingPromotion(promotionId);

        couponValidator.validateBulkGenerate(request);

        List<Coupon> coupons = new ArrayList<>();

        if (request.getScope() == CouponScope.PUBLIC) {
            for (int i = 0; i < request.getQuantity(); i++) {
                Coupon coupon = Coupon.builder()
                        .promotion(promotion)
                        .scope(CouponScope.PUBLIC)
                        .code(generateUniqueCouponCode(request.getPrefix()))
                        .issueReason(request.getIssueReason())
                        .issuedAt(LocalDateTime.now())
                        .usageLimit(request.getUsageLimit() == null ? 1 : request.getUsageLimit())
                        .usedCount(0)
                        .expiresAt(request.getExpiresAt())
                        .status(CouponStatus.DRAFT)
                        .build();

                applySnapshot(coupon, promotion);
                coupons.add(coupon);
            }
        }

        if (request.getScope() == CouponScope.PERSONAL) {
            for (UUID customerId : request.getCustomerIds()) {
                Coupon coupon = Coupon.builder()
                        .promotion(promotion)
                        .scope(CouponScope.PERSONAL)
                        .customerId(customerId)
                        .code(generateUniqueCouponCode(request.getPrefix()))
                        .issueReason(request.getIssueReason())
                        .issuedAt(LocalDateTime.now())
                        .usageLimit(request.getUsageLimit() == null ? 1 : request.getUsageLimit())
                        .usedCount(0)
                        .expiresAt(request.getExpiresAt())
                        .status(CouponStatus.DRAFT)
                        .build();

                applySnapshot(coupon, promotion);
                coupons.add(coupon);
            }
        }

        List<Coupon> savedCoupons = couponRepository.saveAll(coupons);

        return savedCoupons.stream()
                .map(couponMapper::toResponse)
                .toList();
    }

    private String generateUniqueCouponCode(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "CP"
                : prefix.trim().toUpperCase();

        String code;

        do {
            code = normalizedPrefix + "-" + UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 10)
                    .toUpperCase();
        } while (couponRepository.existsByCodeAndIsDeletedFalse(code));

        return code;
    }
}