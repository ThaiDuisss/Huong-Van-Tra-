package org.hvtra.promotionservice.validation.validator;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.exception.BusinessException;
import org.hvtra.promotionservice.dto.request.coupon.*;
import org.hvtra.promotionservice.entity.Promotion;
import org.hvtra.promotionservice.entity.coupon.Coupon;
import org.hvtra.promotionservice.enums.coupon.CouponScope;
import org.hvtra.promotionservice.enums.coupon.CouponStatus;
import org.hvtra.promotionservice.enums.promotion.PromotionStatus;
import org.hvtra.promotionservice.repository.coupon.CouponRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CouponValidator {

    CouponRepository couponRepository;

    public void validateCreateRequest(CouponCreateRequest request) {
        if (request.getPromotionId() == null) {
            throw new BusinessException("Promotion ID is required when creating coupon from /coupons endpoint");
        }
    }

    public void validateBeforeCreate(Promotion promotion, CouponCreateRequest request) {
        validatePromotionCanHaveCoupon(promotion);
        validateScopeAndCustomer(request.getScope(), request.getCustomerId());
        validateUniqueCode(request.getCode());
    }

    public void validateBeforeUpdate(Coupon coupon, CouponUpdateRequest request) {
        validateCouponEditable(coupon);
        validateScopeAndCustomer(request.getScope(), request.getCustomerId());

        if (!coupon.getCode().equals(request.getCode())) {
            validateUniqueCode(request.getCode());
        }
    }

    public void validateStatusTransition(Coupon coupon, CouponStatusUpdateRequest request) {
        CouponStatus currentStatus = coupon.getStatus();
        CouponStatus nextStatus = request.getStatus();

        if (currentStatus == nextStatus) {
            throw new BusinessException("Coupon is already in status: " + nextStatus);
        }

        if (currentStatus == CouponStatus.CANCELLED) {
            throw new BusinessException("Cancelled coupon cannot be updated to another status");
        }

        if (currentStatus == CouponStatus.ARCHIVED) {
            throw new BusinessException("Archived coupon cannot be updated to another status");
        }
    }

    public void validateBeforeDelete(Coupon coupon) {
        if (coupon.getStatus() == CouponStatus.ACTIVE) {
            throw new BusinessException("Active coupon cannot be deleted. Please pause, cancel or archive it first");
        }
    }

    private void validatePromotionCanHaveCoupon(Promotion promotion) {
        if (promotion.getStatus() == PromotionStatus.CANCELLED) {
            throw new BusinessException("Cannot create coupon for cancelled promotion");
        }

        if (promotion.getStatus() == PromotionStatus.EXPIRED) {
            throw new BusinessException("Cannot create coupon for expired promotion");
        }
    }

    private void validateCouponEditable(Coupon coupon) {
        if (coupon.getStatus() == CouponStatus.CANCELLED) {
            throw new BusinessException("Cancelled coupon cannot be updated");
        }

        if (coupon.getStatus() == CouponStatus.ARCHIVED) {
            throw new BusinessException("Archived coupon cannot be updated");
        }
    }

    private void validateScopeAndCustomer(CouponScope scope, java.util.UUID customerId) {
        if (scope == CouponScope.PERSONAL && customerId == null) {
            throw new BusinessException("Customer ID is required for personal coupon");
        }

        if (scope == CouponScope.PUBLIC && customerId != null) {
            throw new BusinessException("Customer ID must be null for public coupon");
        }
    }

    private void validateUniqueCode(String code) {
        if (couponRepository.existsByCodeAndIsDeletedFalse(code)) {
            throw new BusinessException("Coupon code already exists");
        }
    }

    public void validateBeforeActivate(Coupon coupon) {
        if (coupon.getStatus() == CouponStatus.ACTIVE) {
            throw new BusinessException("Coupon is already active");
        }

        if (coupon.getStatus() == CouponStatus.CANCELLED) {
            throw new BusinessException("Cancelled coupon cannot be activated");
        }

        if (coupon.getStatus() == CouponStatus.ARCHIVED) {
            throw new BusinessException("Archived coupon cannot be activated");
        }

        if (coupon.getStatus() == CouponStatus.EXPIRED) {
            throw new BusinessException("Expired coupon cannot be activated");
        }

        if (coupon.getExpiresAt() != null && coupon.getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            throw new BusinessException("Expired coupon cannot be activated");
        }
    }

    public void validateBeforePause(Coupon coupon) {
        if (coupon.getStatus() == CouponStatus.PAUSED) {
            throw new BusinessException("Coupon is already paused");
        }

        if (coupon.getStatus() == CouponStatus.CANCELLED) {
            throw new BusinessException("Cancelled coupon cannot be paused");
        }

        if (coupon.getStatus() == CouponStatus.ARCHIVED) {
            throw new BusinessException("Archived coupon cannot be paused");
        }

        if (coupon.getStatus() == CouponStatus.EXPIRED) {
            throw new BusinessException("Expired coupon cannot be paused");
        }
    }

    public void validateBeforeCancel(Coupon coupon) {
        if (coupon.getStatus() == CouponStatus.CANCELLED) {
            throw new BusinessException("Coupon is already cancelled");
        }

        if (coupon.getStatus() == CouponStatus.ARCHIVED) {
            throw new BusinessException("Archived coupon cannot be cancelled");
        }
    }

    public void validateBeforeArchive(Coupon coupon) {
        if (coupon.getStatus() == CouponStatus.ARCHIVED) {
            throw new BusinessException("Coupon is already archived");
        }

        if (coupon.getStatus() == CouponStatus.ACTIVE) {
            throw new BusinessException("Active coupon cannot be archived. Please pause or cancel it first");
        }
    }

    public void validateGeneratePublic(CouponGeneratePublicRequest request) {
        if (request.getUsageLimit() != null && request.getUsageLimit() < 1) {
            throw new BusinessException("Usage limit must be greater than or equal to 1");
        }
    }

    public void validateGeneratePersonal(CouponGeneratePersonalRequest request) {
        if (request.getCustomerId() == null) {
            throw new BusinessException("Customer ID is required for personal coupon");
        }
    }

    public void validateBulkGenerate(CouponBulkGenerateRequest request) {
        if (request.getScope() == CouponScope.PUBLIC) {
            if (request.getQuantity() == null || request.getQuantity() < 1) {
                throw new BusinessException("Quantity is required for public bulk generation");
            }
        }

        if (request.getScope() == CouponScope.PERSONAL) {
            if (request.getCustomerIds() == null || request.getCustomerIds().isEmpty()) {
                throw new BusinessException("Customer IDs are required for personal bulk generation");
            }
        }
    }
}
