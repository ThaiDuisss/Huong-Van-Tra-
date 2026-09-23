package org.hvtra.promotionservice.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.common_core.exception.BusinessException;
import org.hvtra.promotionservice.dto.request.PromotionPreviewRequest;
import org.hvtra.promotionservice.dto.request.PromotionPreviewLineRequest;
import org.hvtra.promotionservice.dto.response.AvailablePromotionResponse;
import org.hvtra.promotionservice.dto.response.PromotionPreviewResponse;
import org.hvtra.promotionservice.entity.Promotion;
import org.hvtra.promotionservice.entity.PromotionBenefit;
import org.hvtra.promotionservice.entity.PromotionCondition;
import org.hvtra.promotionservice.entity.PromotionTarget;
import org.hvtra.promotionservice.entity.coupon.Coupon;
import org.hvtra.promotionservice.enums.benefit.BenefitType;
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
import java.util.Objects;
import java.util.UUID;

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
            if (!matchConditions(promotion, request)) {
                continue;
            }

            BenefitPreview preview = calculateBenefits(
                    promotion,
                    request,
                    getEligibleAmount(promotion, request)
            );

            if (!preview.hasBenefit()) {
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
                    .estimatedDiscount(preview.discount())
                    .giftProductId(preview.giftProductId())
                    .giftQuantity(preview.giftQuantity())
                    .buyQuantity(preview.buyQuantity())
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

    private boolean matchConditions(Promotion promotion, PromotionPreviewRequest request) {
        return promotion.getConditions()
                .stream()
                .allMatch(condition -> matchCondition(condition, request));
    }

    private boolean matchCondition(PromotionCondition condition, PromotionPreviewRequest request) {
        return switch (condition.getConditionType()) {
            case MIN_ORDER_AMOUNT -> request.getOrderAmount().compareTo(
                    parseConditionValue(condition.getConditionValue())
            ) >= 0;
            case MAX_ORDER_AMOUNT -> request.getOrderAmount().compareTo(
                    parseConditionValue(condition.getConditionValue())
            ) <= 0;
            case MIN_ITEM_QUANTITY -> getLines(request).stream()
                    .map(PromotionPreviewLineRequest::getQuantity)
                    .filter(Objects::nonNull)
                    .reduce(0, Integer::sum) >= parseConditionValue(condition.getConditionValue()).intValue();
            case FIRST_PURCHASE -> request.getCustomerId() != null
                    && request.getCustomerOrderCount() != null
                    && request.getCustomerOrderCount() == 0;
            case CHANNEL -> request.getChannel() != null
                    && condition.getConditionValue().toUpperCase().contains(request.getChannel().name());
            case BRANCH -> request.getBranchId() != null
                    && condition.getConditionValue().contains(request.getBranchId().toString());
            default -> true;
        };
    }

    private BigDecimal getEligibleAmount(Promotion promotion, PromotionPreviewRequest request) {
        List<PromotionTarget> targets = promotion.getTargets();
        if (targets == null || targets.isEmpty()) {
            return request.getOrderAmount();
        }
        return getLines(request).stream()
                .filter(line -> isLineEligible(line, targets))
                .map(PromotionPreviewLineRequest::getLineAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isLineEligible(PromotionPreviewLineRequest line, List<PromotionTarget> targets) {
        if (targets.stream().filter(t -> Boolean.TRUE.equals(t.getExcluded()))
                .anyMatch(target -> matchesTarget(line, target))) {
            return false;
        }
        List<PromotionTarget> included = targets.stream()
                .filter(target -> !Boolean.TRUE.equals(target.getExcluded()))
                .toList();
        return included.isEmpty() || included.stream().anyMatch(target -> matchesTarget(line, target));
    }

    private boolean matchesTarget(PromotionPreviewLineRequest line, PromotionTarget target) {
        return switch (target.getTargetType()) {
            case PRODUCT -> target.getTargetId().equals(line.getProductId());
            case CATEGORY -> target.getTargetId().equals(line.getCategoryId());
            case BRAND -> false;
        };
    }

    private BenefitPreview calculateBenefits(
            Promotion promotion,
            PromotionPreviewRequest request,
            BigDecimal eligibleAmount
    ) {
        BigDecimal discount = BigDecimal.ZERO;
        UUID giftProductId = null;
        int giftQuantity = 0;
        Integer buyQuantity = null;

        for (PromotionBenefit benefit : promotion.getBenefits()) {
            switch (benefit.getBenefitType()) {
                case PERCENT_DISCOUNT -> {
                    BigDecimal amount = eligibleAmount.multiply(benefit.getValue())
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    discount = discount.add(benefit.getMaxDiscount() == null
                            ? amount : amount.min(benefit.getMaxDiscount()));
                }
                case FIXED_DISCOUNT -> discount = discount.add(benefit.getValue().min(eligibleAmount));
                case FREE_PRODUCT -> {
                    giftProductId = benefit.getGiftProductId();
                    int free = benefit.getGetQuantity() == null ? 1 : benefit.getGetQuantity();
                    giftQuantity += free;
                    discount = discount.add(getGiftValue(request, giftProductId, free));
                }
                case BUY_X_GET_Y -> {
                    int required = benefit.getBuyQuantity() == null ? 1 : benefit.getBuyQuantity();
                    int free = benefit.getGetQuantity() == null ? 1 : benefit.getGetQuantity();
                    int matchingQuantity = getLines(request).stream()
                            .filter(line -> promotion.getTargets() == null
                                    || promotion.getTargets().isEmpty()
                                    || isLineEligible(line, promotion.getTargets()))
                            .map(PromotionPreviewLineRequest::getQuantity)
                            .filter(Objects::nonNull)
                            .reduce(0, Integer::sum);
                    int groups = matchingQuantity / required;
                    if (groups > 0) {
                        giftProductId = benefit.getGiftProductId();
                        int awardedQuantity = groups * free;
                        giftQuantity += awardedQuantity;
                        discount = discount.add(getGiftValue(request, giftProductId, awardedQuantity));
                        buyQuantity = required;
                    }
                }
                case FREE_SHIPPING -> {
                    // The POS request has no shipping fee to discount.
                }
            }
        }
        return new BenefitPreview(
                discount.max(BigDecimal.ZERO).min(request.getOrderAmount()),
                giftProductId,
                giftQuantity,
                buyQuantity
        );
    }

    private BigDecimal getGiftValue(
            PromotionPreviewRequest request,
            UUID giftProductId,
            int giftQuantity
    ) {
        if (giftProductId == null || giftQuantity <= 0) {
            return BigDecimal.ZERO;
        }
        return getLines(request).stream()
                .filter(line -> giftProductId.equals(line.getProductId()))
                .filter(line -> line.getQuantity() != null && line.getQuantity() > 0)
                .filter(line -> line.getLineAmount() != null)
                .findFirst()
                .map(line -> line.getLineAmount()
                        .divide(BigDecimal.valueOf(line.getQuantity()), 2, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(Math.min(giftQuantity, line.getQuantity()))))
                .orElse(BigDecimal.ZERO);
    }

    private List<PromotionPreviewLineRequest> getLines(PromotionPreviewRequest request) {
        return request.getLines() == null ? List.of() : request.getLines();
    }

    private record BenefitPreview(
            BigDecimal discount,
            UUID giftProductId,
            int giftQuantity,
            Integer buyQuantity
    ) {
        boolean hasBenefit() {
            return discount.compareTo(BigDecimal.ZERO) > 0 || giftQuantity > 0;
        }
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
