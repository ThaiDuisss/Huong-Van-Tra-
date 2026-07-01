package org.hvtra.promotionservice.validation.validator;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.exception.BusinessException;
import org.hvtra.promotionservice.entity.coupon.CouponUsage;
import org.hvtra.promotionservice.enums.coupon.CouponUsageStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CouponUsageValidator {

    public void validateBeforeRelease(CouponUsage usage) {
        if (usage.getStatus() != CouponUsageStatus.RESERVED) {
            throw new BusinessException("Only reserved coupon usage can be released");
        }
    }

    public void validateBeforeCancel(CouponUsage usage) {
        if (usage.getStatus() == CouponUsageStatus.CANCELLED) {
            throw new BusinessException("Coupon usage is already cancelled");
        }

        if (usage.getStatus() == CouponUsageStatus.APPLIED) {
            throw new BusinessException("Applied coupon usage cannot be cancelled");
        }
    }

    public void validateBeforeExpire(CouponUsage usage) {
        if (usage.getStatus() != CouponUsageStatus.RESERVED) {
            throw new BusinessException("Only reserved coupon usage can be expired");
        }
    }
}
