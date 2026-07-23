package org.hvtra.promotionservice.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.exception.BusinessException;
import org.hvtra.promotionservice.dto.request.PromotionPreviewRequest;
import org.hvtra.promotionservice.dto.response.AvailablePromotionResponse;
import org.hvtra.promotionservice.dto.response.PromotionPreviewResponse;
import org.hvtra.promotionservice.entity.Promotion;
import org.hvtra.promotionservice.entity.PromotionBenefit;
import org.hvtra.promotionservice.entity.PromotionCondition;
import org.hvtra.promotionservice.entity.coupon.Coupon;
import org.hvtra.promotionservice.enums.benefit.BenefitType;
import org.hvtra.promotionservice.enums.condition.ConditionType;
import org.hvtra.promotionservice.enums.coupon.CouponStatus;
import org.hvtra.promotionservice.enums.coupon.CouponUsageStatus;
import org.hvtra.promotionservice.enums.event.EventStatus;
import org.hvtra.promotionservice.enums.promotion.PromotionApplyMethod;
import org.hvtra.promotionservice.enums.promotion.PromotionStatus;
import org.hvtra.promotionservice.repository.coupon.CouponRepository;
import org.hvtra.promotionservice.repository.coupon.CouponUsageRepository;
import org.hvtra.promotionservice.repository.promotion.PromotionRepository;
import org.hvtra.promotionservice.service.PromotionApplyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PromotionApplyServiceImpl implements PromotionApplyService {

    private static final String ORDER_AMOUNT_CONDITION_VALUE_ERROR =
            "Order amount condition value must contain numeric amount, e.g. {\"amount\":500000}";

    PromotionRepository promotionRepository;
    CouponRepository couponRepository;
    CouponUsageRepository couponUsageRepository;
    ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public PromotionPreviewResponse preview(PromotionPreviewRequest request) {
        BigDecimal orderAmount = request.getOrderAmount();
        LocalDateTime now = LocalDateTime.now();
        String requestedCouponCode = normalizeCouponCode(request.getCouponCode());

        List<Promotion> promotions = promotionRepository.findActivePromotionsForPreview(
                PromotionStatus.ACTIVE,
                EventStatus.ACTIVE,
                now
        );

        List<AvailablePromotionResponse> availablePromotions = new ArrayList<>();

        for (Promotion promotion : promotions) {
            if (!matchOrderAmountConditions(promotion, orderAmount)) {
                continue;
            }

            BigDecimal discountAmount = calculateDiscount(promotion, orderAmount);

            if (discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            Optional<Coupon> couponCandidate = Optional.empty();
            boolean requiresCoupon = promotion.getApplyMethod() == PromotionApplyMethod.COUPON_CODE;

            if (requiresCoupon) {
                couponCandidate = findEligibleCouponForPreview(
                        promotion,
                        request,
                        requestedCouponCode,
                        now
                );

                if (couponCandidate.isEmpty()) {
                    continue;
                }
            }

            AvailablePromotionResponse.AvailablePromotionResponseBuilder responseBuilder =
                    AvailablePromotionResponse.builder()
                    .promotionId(promotion.getId())
                    .promotionName(promotion.getName())
                    .applyMethod(promotion.getApplyMethod())
                    .benefitType(getMainBenefitType(promotion))
                    .estimatedDiscount(discountAmount)
                    .stackable(promotion.getStackable())
                    .requiresCoupon(requiresCoupon);

            couponCandidate.ifPresent(coupon -> responseBuilder
                    .couponId(coupon.getId())
                    .couponCode(coupon.getCode())
                    .couponScope(coupon.getScope())
                    .couponExpiresAt(coupon.getExpiresAt()));

            availablePromotions.add(responseBuilder.build());
        }

        return PromotionPreviewResponse.builder()
                .orderAmount(orderAmount)
                .availablePromotions(availablePromotions)
                .build();
    }

    private boolean matchOrderAmountConditions(Promotion promotion, BigDecimal orderAmount) {
        return promotion.getConditions()
                .stream()
                .allMatch(condition -> matchOrderAmountCondition(condition, orderAmount));
    }

    private boolean matchOrderAmountCondition(PromotionCondition condition, BigDecimal orderAmount) {
        return switch (condition.getConditionType()) {
            case MIN_ORDER_AMOUNT -> orderAmount.compareTo(
                    parseConditionValue(condition.getConditionValue())
            ) >= 0;
            case MAX_ORDER_AMOUNT -> orderAmount.compareTo(
                    parseConditionValue(condition.getConditionValue())
            ) <= 0;
            default -> true;
        };
    }

    private BigDecimal calculateDiscount(Promotion promotion, BigDecimal orderAmount) {
        return promotion.getBenefits()
                .stream()
                .map(benefit -> calculateBenefitDiscount(benefit, orderAmount))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .min(orderAmount);
    }

    private BigDecimal calculateBenefitDiscount(PromotionBenefit benefit, BigDecimal orderAmount) {
        BigDecimal discount = switch (benefit.getBenefitType()) {
            case PERCENT_DISCOUNT -> orderAmount
                    .multiply(benefit.getValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            case FIXED_DISCOUNT -> benefit.getValue();

            default -> BigDecimal.ZERO;
        };

        if (benefit.getMaxDiscount() != null) {
            discount = discount.min(benefit.getMaxDiscount());
        }

        return discount.max(BigDecimal.ZERO);
    }

    private BenefitType getMainBenefitType(Promotion promotion) {
        return promotion.getBenefits()
                .stream()
                .findFirst()
                .map(PromotionBenefit::getBenefitType)
                .orElse(null);
    }

    private Optional<Coupon> findEligibleCouponForPreview(
            Promotion promotion,
            PromotionPreviewRequest request,
            String requestedCouponCode,
            LocalDateTime now
    ) {
        return couponRepository.findPreviewEligibleCoupons(
                        promotion.getId(),
                        request.getCustomerId(),
                        requestedCouponCode,
                        CouponStatus.ACTIVE,
                        now
                )
                .stream()
                .filter(coupon -> !hasReachedPerCustomerLimit(coupon, request))
                .findFirst();
    }

    private boolean hasReachedPerCustomerLimit(Coupon coupon, PromotionPreviewRequest request) {
        if (request.getCustomerId() == null) {
            return false;
        }

        int perCustomerLimit = coupon.getPerCustomerLimit() == null ? 1 : coupon.getPerCustomerLimit();
        long customerUsageCount = couponUsageRepository.countByCouponIdAndCustomerIdAndStatusInAndIsDeletedFalse(
                coupon.getId(),
                request.getCustomerId(),
                List.of(CouponUsageStatus.RESERVED, CouponUsageStatus.APPLIED)
        );

        return customerUsageCount >= perCustomerLimit;
    }

    private String normalizeCouponCode(String couponCode) {
        if (couponCode == null || couponCode.isBlank()) {
            return null;
        }

        return couponCode.trim();
    }

    private BigDecimal parseConditionValue(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ORDER_AMOUNT_CONDITION_VALUE_ERROR);
        }

        try {
            return parseConditionValueNode(objectMapper.readTree(value), value);
        } catch (JsonProcessingException ex) {
            return parsePlainDecimal(value);
        }
    }

    private BigDecimal parseConditionValueNode(JsonNode node, String rawValue) {
        if (node == null || node.isNull()) {
            throw new BusinessException(ORDER_AMOUNT_CONDITION_VALUE_ERROR);
        }

        if (node.isObject()) {
            return parseAmountNode(node.get("amount"));
        }

        if (node.isNumber()) {
            return node.decimalValue();
        }

        if (node.isTextual()) {
            String textValue = node.asText().trim();

            if (textValue.startsWith("{")) {
                return parseConditionValue(textValue);
            }

            return parsePlainDecimal(textValue);
        }

        return parsePlainDecimal(rawValue);
    }

    private BigDecimal parseAmountNode(JsonNode amountNode) {
        if (amountNode == null || amountNode.isNull()) {
            throw new BusinessException(ORDER_AMOUNT_CONDITION_VALUE_ERROR);
        }

        if (amountNode.isNumber()) {
            return amountNode.decimalValue();
        }

        if (amountNode.isTextual()) {
            return parsePlainDecimal(amountNode.asText());
        }

        throw new BusinessException(ORDER_AMOUNT_CONDITION_VALUE_ERROR);
    }

    private BigDecimal parsePlainDecimal(String value) {
        try {
            return new BigDecimal(value.replace("\"", "").trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(ORDER_AMOUNT_CONDITION_VALUE_ERROR);
        }
    }
}
