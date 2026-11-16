package org.hvtra.promotionservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hvtra.commonservice.common_core.exception.BusinessException;
import org.hvtra.promotionservice.dto.request.PromotionPreviewLineRequest;
import org.hvtra.promotionservice.dto.request.PromotionPreviewRequest;
import org.hvtra.promotionservice.dto.response.AvailablePromotionResponse;
import org.hvtra.promotionservice.dto.response.PromotionPreviewResponse;
import org.hvtra.promotionservice.entity.Event;
import org.hvtra.promotionservice.entity.Promotion;
import org.hvtra.promotionservice.entity.PromotionBenefit;
import org.hvtra.promotionservice.entity.PromotionCondition;
import org.hvtra.promotionservice.entity.PromotionTarget;
import org.hvtra.promotionservice.entity.coupon.Coupon;
import org.hvtra.promotionservice.enums.benefit.BenefitType;
import org.hvtra.promotionservice.enums.condition.ConditionOperator;
import org.hvtra.promotionservice.enums.condition.ConditionType;
import org.hvtra.promotionservice.enums.coupon.CouponScope;
import org.hvtra.promotionservice.enums.coupon.CouponStatus;
import org.hvtra.promotionservice.enums.coupon.CouponUsageStatus;
import org.hvtra.promotionservice.enums.event.EventStatus;
import org.hvtra.promotionservice.enums.promotion.PromotionApplyMethod;
import org.hvtra.promotionservice.enums.promotion.PromotionChannel;
import org.hvtra.promotionservice.enums.promotion.PromotionStatus;
import org.hvtra.promotionservice.enums.promotion.PromotionTargetType;
import org.hvtra.promotionservice.repository.coupon.CouponRepository;
import org.hvtra.promotionservice.repository.coupon.CouponUsageRepository;
import org.hvtra.promotionservice.repository.promotion.PromotionRepository;
import org.hvtra.promotionservice.service.impl.PromotionApplyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PromotionApplyServiceImpl.preview")
class PromotionApplyServiceImplTest {

    @Mock
    PromotionRepository promotionRepository;

    @Mock
    CouponRepository couponRepository;

    @Mock
    CouponUsageRepository couponUsageRepository;

    PromotionApplyServiceImpl promotionApplyService;

    final UUID productA = UUID.randomUUID();
    final UUID productB = UUID.randomUUID();
    final UUID categoryTea = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        promotionApplyService = new PromotionApplyServiceImpl(
                promotionRepository, couponRepository, couponUsageRepository, new ObjectMapper());
    }

    Promotion activePromotion(String name, PromotionApplyMethod applyMethod) {
        Event event = Event.builder().name("Event").status(EventStatus.ACTIVE).build();
        Promotion promotion = Promotion.builder().name(name).event(event).status(PromotionStatus.ACTIVE)
                .applyMethod(applyMethod).stackable(false).priority(1).build();
        promotion.setId(UUID.randomUUID());
        return promotion;
    }

    PromotionBenefit percent(BigDecimal value, BigDecimal maxDiscount) {
        return PromotionBenefit.builder().benefitType(BenefitType.PERCENT_DISCOUNT).value(value).maxDiscount(maxDiscount).build();
    }

    PromotionCondition condition(ConditionType type, String value) {
        return PromotionCondition.builder().conditionType(type).operator(ConditionOperator.GTE).conditionValue(value).build();
    }

    PromotionPreviewLineRequest line(UUID productId, UUID categoryId, int quantity, long amount) {
        return PromotionPreviewLineRequest.builder().productId(productId).categoryId(categoryId)
                .quantity(quantity).lineAmount(BigDecimal.valueOf(amount)).build();
    }

    PromotionPreviewRequest request(long orderAmount, PromotionPreviewLineRequest... lines) {
        return PromotionPreviewRequest.builder().orderAmount(BigDecimal.valueOf(orderAmount))
                .channel(PromotionChannel.POS).lines(List.of(lines)).build();
    }

    void activePromotions(Promotion... promotions) {
        when(promotionRepository.findActivePromotionsForPreview(eq(PromotionStatus.ACTIVE), eq(EventStatus.ACTIVE),
                any(LocalDateTime.class))).thenReturn(List.of(promotions));
    }

    @Nested
    @DisplayName("benefit calculation")
    class BenefitCalculation {

        @Test
        @DisplayName("returns the order amount and no promotion when nothing is active")
        void preview_noActivePromotion_returnsEmpty() {
            activePromotions();

            PromotionPreviewResponse result = promotionApplyService.preview(request(500_000));

            assertThat(result.getOrderAmount()).isEqualByComparingTo("500000");
            assertThat(result.getAvailablePromotions()).isEmpty();
            verifyNoInteractions(couponRepository, couponUsageRepository);
        }

        @Test
        @DisplayName("percent discount is capped by maxDiscount")
        void preview_percentDiscount_cappedByMax() {
            Promotion promotion = activePromotion("20% off", PromotionApplyMethod.AUTO);
            promotion.addBenefit(percent(BigDecimal.valueOf(20), BigDecimal.valueOf(100_000)));
            activePromotions(promotion);

            PromotionPreviewResponse result = promotionApplyService.preview(request(1_000_000));

            assertThat(result.getAvailablePromotions()).hasSize(1);
            AvailablePromotionResponse offer = result.getAvailablePromotions().get(0);
            assertThat(offer.getPromotionId()).isEqualTo(promotion.getId());
            assertThat(offer.getEstimatedDiscount()).isEqualByComparingTo("100000");
            assertThat(offer.getBenefitType()).isEqualTo(BenefitType.PERCENT_DISCOUNT);
            assertThat(offer.getRequiresCoupon()).isFalse();
            assertThat(offer.getCouponCode()).isNull();
        }

        @Test
        @DisplayName("percent discount without a cap uses the full percentage rounded to 2 decimals")
        void preview_percentDiscount_noCap() {
            Promotion promotion = activePromotion("15% off", PromotionApplyMethod.AUTO);
            promotion.addBenefit(percent(BigDecimal.valueOf(15), null));
            activePromotions(promotion);

            PromotionPreviewResponse result = promotionApplyService.preview(request(333_333));

            assertThat(result.getAvailablePromotions().get(0).getEstimatedDiscount()).isEqualByComparingTo("49999.95");
        }

        @Test
        @DisplayName("fixed discount never exceeds the eligible amount")
        void preview_fixedDiscount_boundedByAmount() {
            Promotion promotion = activePromotion("Fixed 80k", PromotionApplyMethod.AUTO);
            promotion.addBenefit(PromotionBenefit.builder().benefitType(BenefitType.FIXED_DISCOUNT)
                    .value(BigDecimal.valueOf(80_000)).build());
            activePromotions(promotion);

            PromotionPreviewResponse result = promotionApplyService.preview(request(50_000));

            assertThat(result.getAvailablePromotions().get(0).getEstimatedDiscount()).isEqualByComparingTo("50000");
        }

        @Test
        @DisplayName("promotion with only FREE_SHIPPING gives no POS benefit and is skipped")
        void preview_freeShippingOnly_skipped() {
            Promotion promotion = activePromotion("Free ship", PromotionApplyMethod.AUTO);
            promotion.addBenefit(PromotionBenefit.builder().benefitType(BenefitType.FREE_SHIPPING)
                    .value(BigDecimal.ZERO).build());
            activePromotions(promotion);

            assertThat(promotionApplyService.preview(request(200_000)).getAvailablePromotions()).isEmpty();
        }

        @Test
        @DisplayName("BUY_X_GET_Y awards gifts per complete group and values them from the cart line")
        void preview_buyXGetY_awardsGroups() {
            Promotion promotion = activePromotion("Buy 2 get 1", PromotionApplyMethod.AUTO);
            promotion.addBenefit(PromotionBenefit.builder().benefitType(BenefitType.BUY_X_GET_Y)
                    .value(BigDecimal.ZERO).giftProductId(productA).buyQuantity(2).getQuantity(1).build());
            activePromotions(promotion);

            PromotionPreviewResponse result = promotionApplyService.preview(
                    request(250_000, line(productA, categoryTea, 5, 250_000)));

            AvailablePromotionResponse offer = result.getAvailablePromotions().get(0);
            assertThat(offer.getGiftProductId()).isEqualTo(productA);
            assertThat(offer.getGiftQuantity()).isEqualTo(2);
            assertThat(offer.getBuyQuantity()).isEqualTo(2);
            assertThat(offer.getEstimatedDiscount()).isEqualByComparingTo("100000.00");
        }

        @Test
        @DisplayName("BUY_X_GET_Y with fewer items than buyQuantity gives nothing")
        void preview_buyXGetY_notEnoughItems() {
            Promotion promotion = activePromotion("Buy 3 get 1", PromotionApplyMethod.AUTO);
            promotion.addBenefit(PromotionBenefit.builder().benefitType(BenefitType.BUY_X_GET_Y)
                    .value(BigDecimal.ZERO).giftProductId(productA).buyQuantity(3).getQuantity(1).build());
            activePromotions(promotion);

            assertThat(promotionApplyService.preview(request(100_000, line(productA, null, 2, 100_000)))
                    .getAvailablePromotions()).isEmpty();
        }

        @Test
        @DisplayName("FREE_PRODUCT gift is valued from the matching cart line and limited to the line quantity")
        void preview_freeProduct_valuedFromLine() {
            Promotion promotion = activePromotion("Gift", PromotionApplyMethod.AUTO);
            promotion.addBenefit(PromotionBenefit.builder().benefitType(BenefitType.FREE_PRODUCT)
                    .value(BigDecimal.ZERO).giftProductId(productB).getQuantity(3).build());
            activePromotions(promotion);

            PromotionPreviewResponse result = promotionApplyService.preview(
                    request(300_000, line(productA, null, 1, 200_000), line(productB, null, 2, 100_000)));

            AvailablePromotionResponse offer = result.getAvailablePromotions().get(0);
            assertThat(offer.getGiftQuantity()).isEqualTo(3);
            assertThat(offer.getEstimatedDiscount()).isEqualByComparingTo("100000.00");
        }

        @Test
        @DisplayName("total discount is capped at the order amount and promotions keep repository order")
        void preview_discountCappedAtOrderAmount() {
            Promotion first = activePromotion("High priority", PromotionApplyMethod.AUTO);
            first.addBenefit(PromotionBenefit.builder().benefitType(BenefitType.FIXED_DISCOUNT)
                    .value(BigDecimal.valueOf(70_000)).build());
            first.addBenefit(PromotionBenefit.builder().benefitType(BenefitType.FIXED_DISCOUNT)
                    .value(BigDecimal.valueOf(70_000)).build());
            Promotion second = activePromotion("Low priority", PromotionApplyMethod.AUTO);
            second.addBenefit(percent(BigDecimal.valueOf(10), null));
            activePromotions(first, second);

            PromotionPreviewResponse result = promotionApplyService.preview(request(100_000));

            assertThat(result.getAvailablePromotions()).extracting(AvailablePromotionResponse::getPromotionName)
                    .containsExactly("High priority", "Low priority");
            assertThat(result.getAvailablePromotions().get(0).getEstimatedDiscount()).isEqualByComparingTo("100000");
            assertThat(result.getAvailablePromotions().get(1).getEstimatedDiscount()).isEqualByComparingTo("10000.00");
        }
    }

    @Nested
    @DisplayName("conditions")
    class Conditions {

        @Test
        @DisplayName("MIN_ORDER_AMOUNT as JSON object is applied")
        void preview_minOrderAmountJson() {
            Promotion promotion = activePromotion("Min 500k", PromotionApplyMethod.AUTO);
            promotion.addBenefit(percent(BigDecimal.TEN, null));
            promotion.addCondition(condition(ConditionType.MIN_ORDER_AMOUNT, "{\"amount\":500000}"));
            activePromotions(promotion);

            assertThat(promotionApplyService.preview(request(499_999)).getAvailablePromotions()).isEmpty();
            assertThat(promotionApplyService.preview(request(500_000)).getAvailablePromotions()).hasSize(1);
        }

        @Test
        @DisplayName("MIN_ORDER_AMOUNT accepts a plain number, a quoted number and a string amount")
        void preview_minOrderAmountFormats() {
            Promotion plain = activePromotion("plain", PromotionApplyMethod.AUTO);
            plain.addBenefit(percent(BigDecimal.TEN, null));
            plain.addCondition(condition(ConditionType.MIN_ORDER_AMOUNT, "300000"));
            Promotion quoted = activePromotion("quoted", PromotionApplyMethod.AUTO);
            quoted.addBenefit(percent(BigDecimal.TEN, null));
            quoted.addCondition(condition(ConditionType.MIN_ORDER_AMOUNT, "\"300000\""));
            Promotion stringAmount = activePromotion("string", PromotionApplyMethod.AUTO);
            stringAmount.addBenefit(percent(BigDecimal.TEN, null));
            stringAmount.addCondition(condition(ConditionType.MIN_ORDER_AMOUNT, "{\"amount\":\"300000\"}"));
            activePromotions(plain, quoted, stringAmount);

            assertThat(promotionApplyService.preview(request(300_000)).getAvailablePromotions()).hasSize(3);
        }

        @Test
        @DisplayName("MAX_ORDER_AMOUNT excludes orders above the limit")
        void preview_maxOrderAmount() {
            Promotion promotion = activePromotion("Max 200k", PromotionApplyMethod.AUTO);
            promotion.addBenefit(percent(BigDecimal.TEN, null));
            promotion.addCondition(condition(ConditionType.MAX_ORDER_AMOUNT, "{\"amount\":200000}"));
            activePromotions(promotion);

            assertThat(promotionApplyService.preview(request(200_001)).getAvailablePromotions()).isEmpty();
            assertThat(promotionApplyService.preview(request(200_000)).getAvailablePromotions()).hasSize(1);
        }

        @Test
        @DisplayName("invalid amount condition value raises BusinessException")
        void preview_invalidConditionValue_throws() {
            Promotion promotion = activePromotion("Broken", PromotionApplyMethod.AUTO);
            promotion.addBenefit(percent(BigDecimal.TEN, null));
            promotion.addCondition(condition(ConditionType.MIN_ORDER_AMOUNT, "{\"min\":1}"));
            activePromotions(promotion);

            assertThatThrownBy(() -> promotionApplyService.preview(request(100_000)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("amount");
        }

        @Test
        @DisplayName("MIN_ITEM_QUANTITY sums the cart quantities")
        void preview_minItemQuantity() {
            Promotion promotion = activePromotion("3 items", PromotionApplyMethod.AUTO);
            promotion.addBenefit(percent(BigDecimal.TEN, null));
            promotion.addCondition(condition(ConditionType.MIN_ITEM_QUANTITY, "3"));
            activePromotions(promotion);

            assertThat(promotionApplyService.preview(request(100_000, line(productA, null, 1, 50_000),
                    line(productB, null, 1, 50_000))).getAvailablePromotions()).isEmpty();
            assertThat(promotionApplyService.preview(request(100_000, line(productA, null, 2, 50_000),
                    line(productB, null, 1, 50_000))).getAvailablePromotions()).hasSize(1);
        }

        @Test
        @DisplayName("FIRST_PURCHASE needs a customer with zero previous orders")
        void preview_firstPurchase() {
            Promotion promotion = activePromotion("First order", PromotionApplyMethod.AUTO);
            promotion.addBenefit(percent(BigDecimal.TEN, null));
            promotion.addCondition(condition(ConditionType.FIRST_PURCHASE, "{}"));
            activePromotions(promotion);

            PromotionPreviewRequest anonymous = request(100_000);
            PromotionPreviewRequest returning = request(100_000);
            returning.setCustomerId(UUID.randomUUID());
            returning.setCustomerOrderCount(2);
            PromotionPreviewRequest firstTimer = request(100_000);
            firstTimer.setCustomerId(UUID.randomUUID());
            firstTimer.setCustomerOrderCount(0);

            assertThat(promotionApplyService.preview(anonymous).getAvailablePromotions()).isEmpty();
            assertThat(promotionApplyService.preview(returning).getAvailablePromotions()).isEmpty();
            assertThat(promotionApplyService.preview(firstTimer).getAvailablePromotions()).hasSize(1);
        }

        @Test
        @DisplayName("CHANNEL and BRANCH conditions match the request context")
        void preview_channelAndBranch() {
            UUID branch = UUID.randomUUID();
            Promotion promotion = activePromotion("POS at branch", PromotionApplyMethod.AUTO);
            promotion.addBenefit(percent(BigDecimal.TEN, null));
            promotion.addCondition(condition(ConditionType.CHANNEL, "{\"channels\":[\"POS\"]}"));
            promotion.addCondition(condition(ConditionType.BRANCH, "{\"branches\":[\"" + branch + "\"]}"));
            activePromotions(promotion);

            PromotionPreviewRequest otherBranch = request(100_000);
            otherBranch.setBranchId(UUID.randomUUID());
            PromotionPreviewRequest online = request(100_000);
            online.setBranchId(branch);
            online.setChannel(PromotionChannel.ONLINE);
            PromotionPreviewRequest match = request(100_000);
            match.setBranchId(branch);

            assertThat(promotionApplyService.preview(otherBranch).getAvailablePromotions()).isEmpty();
            assertThat(promotionApplyService.preview(online).getAvailablePromotions()).isEmpty();
            assertThat(promotionApplyService.preview(match).getAvailablePromotions()).hasSize(1);
        }

        @Test
        @DisplayName("customer based conditions that need external data are treated as satisfied")
        void preview_customerConditionsPassThrough() {
            Promotion promotion = activePromotion("VIP", PromotionApplyMethod.AUTO);
            promotion.addBenefit(percent(BigDecimal.TEN, null));
            promotion.addCondition(condition(ConditionType.CUSTOMER_TIER, "{\"tiers\":[\"GOLD\"]}"));
            promotion.addCondition(condition(ConditionType.MIN_LOYALTY_POINT, "{\"points\":100}"));
            activePromotions(promotion);

            assertThat(promotionApplyService.preview(request(100_000)).getAvailablePromotions()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("targets")
    class Targets {

        @Test
        @DisplayName("PRODUCT target limits the eligible amount to matching lines")
        void preview_productTarget_limitsAmount() {
            Promotion promotion = activePromotion("Product A only", PromotionApplyMethod.AUTO);
            promotion.addBenefit(percent(BigDecimal.TEN, null));
            promotion.addTarget(PromotionTarget.builder().targetType(PromotionTargetType.PRODUCT).targetId(productA)
                    .excluded(false).build());
            activePromotions(promotion);

            PromotionPreviewResponse result = promotionApplyService.preview(
                    request(300_000, line(productA, categoryTea, 1, 100_000), line(productB, categoryTea, 1, 200_000)));

            assertThat(result.getAvailablePromotions().get(0).getEstimatedDiscount()).isEqualByComparingTo("10000.00");
        }

        @Test
        @DisplayName("CATEGORY target matches lines by category and an excluded product is removed")
        void preview_categoryTargetWithExclusion() {
            Promotion promotion = activePromotion("Tea minus B", PromotionApplyMethod.AUTO);
            promotion.addBenefit(percent(BigDecimal.TEN, null));
            promotion.addTarget(PromotionTarget.builder().targetType(PromotionTargetType.CATEGORY).targetId(categoryTea)
                    .excluded(false).build());
            promotion.addTarget(PromotionTarget.builder().targetType(PromotionTargetType.PRODUCT).targetId(productB)
                    .excluded(true).build());
            activePromotions(promotion);

            PromotionPreviewResponse result = promotionApplyService.preview(
                    request(300_000, line(productA, categoryTea, 1, 100_000), line(productB, categoryTea, 1, 200_000)));

            assertThat(result.getAvailablePromotions().get(0).getEstimatedDiscount()).isEqualByComparingTo("10000.00");
        }

        @Test
        @DisplayName("only excluded targets means every other line is eligible")
        void preview_onlyExcludedTargets() {
            Promotion promotion = activePromotion("All but A", PromotionApplyMethod.AUTO);
            promotion.addBenefit(percent(BigDecimal.TEN, null));
            promotion.addTarget(PromotionTarget.builder().targetType(PromotionTargetType.PRODUCT).targetId(productA)
                    .excluded(true).build());
            activePromotions(promotion);

            PromotionPreviewResponse result = promotionApplyService.preview(
                    request(300_000, line(productA, null, 1, 100_000), line(productB, null, 1, 200_000)));

            assertThat(result.getAvailablePromotions().get(0).getEstimatedDiscount()).isEqualByComparingTo("20000.00");
        }

        @Test
        @DisplayName("no eligible line gives no benefit and the promotion is skipped")
        void preview_noEligibleLine_skipped() {
            Promotion promotion = activePromotion("Product A only", PromotionApplyMethod.AUTO);
            promotion.addBenefit(percent(BigDecimal.TEN, null));
            promotion.addTarget(PromotionTarget.builder().targetType(PromotionTargetType.PRODUCT).targetId(productA)
                    .excluded(false).build());
            activePromotions(promotion);

            assertThat(promotionApplyService.preview(request(200_000, line(productB, null, 1, 200_000)))
                    .getAvailablePromotions()).isEmpty();
        }
    }

    @Nested
    @DisplayName("coupon promotions")
    class CouponPromotions {

        Coupon coupon(Promotion promotion, String code) {
            Coupon coupon = Coupon.builder().promotion(promotion).code(code).scope(CouponScope.PUBLIC)
                    .status(CouponStatus.ACTIVE).perCustomerLimit(1).expiresAt(LocalDateTime.of(2027, 12, 31, 0, 0)).build();
            coupon.setId(UUID.randomUUID());
            return coupon;
        }

        @Test
        @DisplayName("COUPON_CODE promotion is listed with the eligible coupon")
        void preview_couponPromotion_withCoupon() {
            Promotion promotion = activePromotion("Coupon 50k", PromotionApplyMethod.COUPON_CODE);
            promotion.addBenefit(PromotionBenefit.builder().benefitType(BenefitType.FIXED_DISCOUNT)
                    .value(BigDecimal.valueOf(50_000)).build());
            activePromotions(promotion);
            Coupon coupon = coupon(promotion, "TET2027");
            when(couponRepository.findPreviewEligibleCoupons(eq(promotion.getId()), isNull(), eq("TET2027"),
                    eq(CouponStatus.ACTIVE), any(LocalDateTime.class))).thenReturn(List.of(coupon));

            PromotionPreviewRequest request = request(200_000);
            request.setCouponCode(" TET2027 ");
            PromotionPreviewResponse result = promotionApplyService.preview(request);

            AvailablePromotionResponse offer = result.getAvailablePromotions().get(0);
            assertThat(offer.getRequiresCoupon()).isTrue();
            assertThat(offer.getCouponId()).isEqualTo(coupon.getId());
            assertThat(offer.getCouponCode()).isEqualTo("TET2027");
            assertThat(offer.getCouponScope()).isEqualTo(CouponScope.PUBLIC);
            assertThat(offer.getCouponExpiresAt()).isEqualTo(coupon.getExpiresAt());
            assertThat(offer.getEstimatedDiscount()).isEqualByComparingTo("50000");
            verifyNoInteractions(couponUsageRepository);
        }

        @Test
        @DisplayName("COUPON_CODE promotion without an eligible coupon is skipped")
        void preview_couponPromotion_noCoupon_skipped() {
            Promotion promotion = activePromotion("Coupon", PromotionApplyMethod.COUPON_CODE);
            promotion.addBenefit(percent(BigDecimal.TEN, null));
            activePromotions(promotion);
            when(couponRepository.findPreviewEligibleCoupons(eq(promotion.getId()), isNull(), isNull(),
                    eq(CouponStatus.ACTIVE), any(LocalDateTime.class))).thenReturn(List.of());

            assertThat(promotionApplyService.preview(request(200_000)).getAvailablePromotions()).isEmpty();
        }

        @Test
        @DisplayName("coupon already used by the customer up to perCustomerLimit is not offered")
        void preview_couponPromotion_perCustomerLimitReached() {
            Promotion promotion = activePromotion("Coupon", PromotionApplyMethod.COUPON_CODE);
            promotion.addBenefit(percent(BigDecimal.TEN, null));
            activePromotions(promotion);
            UUID customerId = UUID.randomUUID();
            Coupon used = coupon(promotion, "USED");
            Coupon fresh = coupon(promotion, "FRESH");
            when(couponRepository.findPreviewEligibleCoupons(eq(promotion.getId()), eq(customerId), isNull(),
                    eq(CouponStatus.ACTIVE), any(LocalDateTime.class))).thenReturn(List.of(used, fresh));
            when(couponUsageRepository.countByCouponIdAndCustomerIdAndStatusInAndIsDeletedFalse(used.getId(),
                    customerId, List.of(CouponUsageStatus.RESERVED, CouponUsageStatus.APPLIED))).thenReturn(1L);
            when(couponUsageRepository.countByCouponIdAndCustomerIdAndStatusInAndIsDeletedFalse(fresh.getId(),
                    customerId, List.of(CouponUsageStatus.RESERVED, CouponUsageStatus.APPLIED))).thenReturn(0L);

            PromotionPreviewRequest request = request(200_000);
            request.setCustomerId(customerId);
            PromotionPreviewResponse result = promotionApplyService.preview(request);

            assertThat(result.getAvailablePromotions()).hasSize(1);
            assertThat(result.getAvailablePromotions().get(0).getCouponCode()).isEqualTo("FRESH");
            verify(couponUsageRepository).countByCouponIdAndCustomerIdAndStatusInAndIsDeletedFalse(used.getId(),
                    customerId, List.of(CouponUsageStatus.RESERVED, CouponUsageStatus.APPLIED));
        }

        @Test
        @DisplayName("AUTO promotions never query coupons")
        void preview_autoPromotion_noCouponLookup() {
            Promotion promotion = activePromotion("Auto", PromotionApplyMethod.AUTO);
            promotion.addBenefit(percent(BigDecimal.TEN, null));
            activePromotions(promotion);

            PromotionPreviewRequest request = request(200_000);
            request.setCouponCode("ANY");
            promotionApplyService.preview(request);

            verifyNoInteractions(couponRepository);
        }
    }

    PromotionBenefit fixed(long value) {
        return PromotionBenefit.builder().benefitType(BenefitType.FIXED_DISCOUNT).value(BigDecimal.valueOf(value)).build();
    }

    PromotionTarget target(PromotionTargetType type, UUID targetId, boolean excluded) {
        return PromotionTarget.builder().targetType(type).targetId(targetId).excluded(excluded).build();
    }

    PromotionPreviewRequest requestOf(String orderAmount) {
        return PromotionPreviewRequest.builder().orderAmount(new BigDecimal(orderAmount))
                .channel(PromotionChannel.POS).build();
    }

    @Nested
    @DisplayName("benefit boundaries")
    class BenefitBoundaries {

        @ParameterizedTest(name = "{0}% of {1} is {2}")
        @CsvSource({
                "100, 80000, 80000.00",
                "12.5, 1001, 125.13",
                "33.33, 100, 33.33",
                "0.5, 1, 0.01",
                "7, 99.99, 7.00"
        })
        void preview_percentDiscount_roundsHalfUpToTwoDecimals(String percent, String orderAmount, String expected) {
            Promotion promotion = activePromotion("Percent", PromotionApplyMethod.AUTO);
            promotion.addBenefit(percent(new BigDecimal(percent), null));
            activePromotions(promotion);

            PromotionPreviewResponse result = promotionApplyService.preview(requestOf(orderAmount));

            assertThat(result.getAvailablePromotions()).hasSize(1);
            assertThat(result.getAvailablePromotions().get(0).getEstimatedDiscount()).isEqualByComparingTo(expected);
        }

        @Test
        @DisplayName("percent discount that rounds down to zero is not offered")
        void preview_percentDiscount_roundsToZero_skipped() {
            Promotion promotion = activePromotion("0.4%", PromotionApplyMethod.AUTO);
            promotion.addBenefit(percent(new BigDecimal("0.4"), null));
            activePromotions(promotion);

            assertThat(promotionApplyService.preview(requestOf("1")).getAvailablePromotions()).isEmpty();
        }

        @Test
        @DisplayName("maxDiscount only applies when the percentage exceeds it")
        void preview_percentDiscount_capBoundary() {
            Promotion promotion = activePromotion("10% max 50k", PromotionApplyMethod.AUTO);
            promotion.addBenefit(percent(BigDecimal.TEN, BigDecimal.valueOf(50_000)));
            activePromotions(promotion);

            assertThat(promotionApplyService.preview(request(499_990)).getAvailablePromotions().get(0)
                    .getEstimatedDiscount()).isEqualByComparingTo("49999");
            assertThat(promotionApplyService.preview(request(500_000)).getAvailablePromotions().get(0)
                    .getEstimatedDiscount()).isEqualByComparingTo("50000");
            assertThat(promotionApplyService.preview(request(500_010)).getAvailablePromotions().get(0)
                    .getEstimatedDiscount()).isEqualByComparingTo("50000");
        }

        @Test
        @DisplayName("an order of zero gets no discount promotion")
        void preview_zeroOrderAmount_skipped() {
            Promotion percentPromotion = activePromotion("10%", PromotionApplyMethod.AUTO);
            percentPromotion.addBenefit(percent(BigDecimal.TEN, null));
            Promotion fixedPromotion = activePromotion("Fixed", PromotionApplyMethod.AUTO);
            fixedPromotion.addBenefit(fixed(20_000));
            activePromotions(percentPromotion, fixedPromotion);

            PromotionPreviewResponse result = promotionApplyService.preview(request(0));

            assertThat(result.getOrderAmount()).isEqualByComparingTo("0");
            assertThat(result.getAvailablePromotions()).isEmpty();
        }

        @Test
        @DisplayName("fixed discount equal to the order amount makes the order free")
        void preview_fixedDiscount_equalToOrderAmount() {
            Promotion promotion = activePromotion("Fixed 50k", PromotionApplyMethod.AUTO);
            promotion.addBenefit(fixed(50_000));
            activePromotions(promotion);

            assertThat(promotionApplyService.preview(request(50_000)).getAvailablePromotions().get(0)
                    .getEstimatedDiscount()).isEqualByComparingTo("50000");
            assertThat(promotionApplyService.preview(request(50_001)).getAvailablePromotions().get(0)
                    .getEstimatedDiscount()).isEqualByComparingTo("50000");
        }

        @Test
        @DisplayName("several benefits of one promotion are summed and the first one names the offer")
        void preview_multipleBenefits_summed() {
            Promotion promotion = activePromotion("10% + 30k", PromotionApplyMethod.AUTO);
            promotion.addBenefit(percent(BigDecimal.TEN, null));
            promotion.addBenefit(fixed(30_000));
            activePromotions(promotion);

            AvailablePromotionResponse offer = promotionApplyService.preview(request(200_000))
                    .getAvailablePromotions().get(0);

            assertThat(offer.getEstimatedDiscount()).isEqualByComparingTo("50000");
            assertThat(offer.getBenefitType()).isEqualTo(BenefitType.PERCENT_DISCOUNT);
        }

        @Test
        @DisplayName("FREE_PRODUCT that is not in the cart is offered as a gift without a discount value")
        void preview_freeProduct_notInCart() {
            Promotion promotion = activePromotion("Gift", PromotionApplyMethod.AUTO);
            promotion.addBenefit(PromotionBenefit.builder().benefitType(BenefitType.FREE_PRODUCT)
                    .value(BigDecimal.ZERO).giftProductId(productB).build());
            activePromotions(promotion);

            AvailablePromotionResponse offer = promotionApplyService.preview(
                    request(200_000, line(productA, null, 1, 200_000))).getAvailablePromotions().get(0);

            assertThat(offer.getGiftProductId()).isEqualTo(productB);
            assertThat(offer.getGiftQuantity()).isEqualTo(1);
            assertThat(offer.getBuyQuantity()).isNull();
            assertThat(offer.getEstimatedDiscount()).isEqualByComparingTo("0");
            assertThat(offer.getBenefitType()).isEqualTo(BenefitType.FREE_PRODUCT);
        }

        @Test
        @DisplayName("BUY_X_GET_Y without quantities means buy 1 get 1")
        void preview_buyXGetY_defaultQuantities() {
            Promotion promotion = activePromotion("Buy 1 get 1", PromotionApplyMethod.AUTO);
            promotion.addBenefit(PromotionBenefit.builder().benefitType(BenefitType.BUY_X_GET_Y)
                    .value(BigDecimal.ZERO).giftProductId(productA).build());
            activePromotions(promotion);

            AvailablePromotionResponse offer = promotionApplyService.preview(
                    request(150_000, line(productA, null, 3, 150_000))).getAvailablePromotions().get(0);

            assertThat(offer.getBuyQuantity()).isEqualTo(1);
            assertThat(offer.getGiftQuantity()).isEqualTo(3);
            assertThat(offer.getEstimatedDiscount()).isEqualByComparingTo("150000");
        }

        @Test
        @DisplayName("BUY_X_GET_Y awards one group at exactly buyQuantity and multiplies getQuantity per group")
        void preview_buyXGetY_groupBoundary() {
            Promotion promotion = activePromotion("Buy 3 get 2", PromotionApplyMethod.AUTO);
            promotion.addBenefit(PromotionBenefit.builder().benefitType(BenefitType.BUY_X_GET_Y)
                    .value(BigDecimal.ZERO).giftProductId(productB).buyQuantity(3).getQuantity(2).build());
            activePromotions(promotion);

            assertThat(promotionApplyService.preview(request(200_000, line(productA, null, 2, 200_000)))
                    .getAvailablePromotions()).isEmpty();
            assertThat(promotionApplyService.preview(request(300_000, line(productA, null, 3, 300_000)))
                    .getAvailablePromotions().get(0).getGiftQuantity()).isEqualTo(2);
            assertThat(promotionApplyService.preview(request(500_000, line(productA, null, 5, 500_000)))
                    .getAvailablePromotions().get(0).getGiftQuantity()).isEqualTo(2);
            assertThat(promotionApplyService.preview(request(600_000, line(productA, null, 6, 600_000)))
                    .getAvailablePromotions().get(0).getGiftQuantity()).isEqualTo(4);
        }

        @Test
        @DisplayName("BUY_X_GET_Y counts the quantity of every line when the cart has several products")
        void preview_buyXGetY_sumsLines() {
            Promotion promotion = activePromotion("Buy 4 get 1", PromotionApplyMethod.AUTO);
            promotion.addBenefit(PromotionBenefit.builder().benefitType(BenefitType.BUY_X_GET_Y)
                    .value(BigDecimal.ZERO).giftProductId(productB).buyQuantity(4).getQuantity(1).build());
            activePromotions(promotion);

            AvailablePromotionResponse offer = promotionApplyService.preview(request(500_000,
                    line(productA, null, 3, 300_000), line(productB, null, 1, 200_000)))
                    .getAvailablePromotions().get(0);

            assertThat(offer.getGiftQuantity()).isEqualTo(1);
            assertThat(offer.getEstimatedDiscount()).isEqualByComparingTo("200000");
        }

        @Test
        @DisplayName("BUY_X_GET_Y only counts lines matching the promotion targets")
        void preview_buyXGetY_respectsTargets() {
            Promotion promotion = activePromotion("Buy 2 A get 1", PromotionApplyMethod.AUTO);
            promotion.addBenefit(PromotionBenefit.builder().benefitType(BenefitType.BUY_X_GET_Y)
                    .value(BigDecimal.ZERO).giftProductId(productA).buyQuantity(2).getQuantity(1).build());
            promotion.addTarget(target(PromotionTargetType.PRODUCT, productA, false));
            activePromotions(promotion);

            assertThat(promotionApplyService.preview(request(600_000,
                    line(productA, null, 1, 100_000), line(productB, null, 5, 500_000)))
                    .getAvailablePromotions()).isEmpty();
            assertThat(promotionApplyService.preview(request(700_000,
                    line(productA, null, 2, 200_000), line(productB, null, 5, 500_000)))
                    .getAvailablePromotions().get(0).getGiftQuantity()).isEqualTo(1);
        }

        @Test
        @DisplayName("the offer carries the promotion's apply method and stackable flag")
        void preview_offerCarriesPromotionSettings() {
            Promotion promotion = activePromotion("Direct", PromotionApplyMethod.DIRECT_DISCOUNT);
            promotion.setStackable(true);
            promotion.addBenefit(fixed(10_000));
            activePromotions(promotion);

            AvailablePromotionResponse offer = promotionApplyService.preview(request(100_000))
                    .getAvailablePromotions().get(0);

            assertThat(offer.getPromotionName()).isEqualTo("Direct");
            assertThat(offer.getApplyMethod()).isEqualTo(PromotionApplyMethod.DIRECT_DISCOUNT);
            assertThat(offer.getStackable()).isTrue();
            assertThat(offer.getRequiresCoupon()).isFalse();
            verifyNoInteractions(couponRepository);
        }

        @Test
        @DisplayName("only ACTIVE promotions of ACTIVE events at the current time are requested")
        void preview_queriesActivePromotionsAtCurrentTime() {
            activePromotions();

            promotionApplyService.preview(request(100_000));

            ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(promotionRepository).findActivePromotionsForPreview(eq(PromotionStatus.ACTIVE),
                    eq(EventStatus.ACTIVE), now.capture());
            assertThat(now.getValue()).isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.SECONDS));
        }

        @Test
        @Disabled("Suspected bug: PromotionApplyServiceImpl.calculateBenefits caps the summed discount at the order "
                + "amount instead of the eligible (targeted) amount, so benefits can discount more than the targeted lines cost")
        @DisplayName("summed discounts never exceed the amount of the targeted lines")
        void preview_multipleBenefits_cappedAtEligibleAmount() {
            Promotion promotion = activePromotion("Product A: 2 x 80k", PromotionApplyMethod.AUTO);
            promotion.addBenefit(fixed(80_000));
            promotion.addBenefit(fixed(80_000));
            promotion.addTarget(target(PromotionTargetType.PRODUCT, productA, false));
            activePromotions(promotion);

            PromotionPreviewResponse result = promotionApplyService.preview(
                    request(300_000, line(productA, null, 1, 100_000), line(productB, null, 1, 200_000)));

            assertThat(result.getAvailablePromotions().get(0).getEstimatedDiscount()).isEqualByComparingTo("100000");
        }

        @Test
        @Disabled("Suspected bug: PromotionApplyServiceImpl.calculateBenefits ignores the promotion targets for "
                + "FREE_PRODUCT, so the gift is offered although no targeted product is in the cart")
        @DisplayName("FREE_PRODUCT of a targeted promotion needs a targeted line in the cart")
        void preview_freeProduct_requiresTargetedLine() {
            Promotion promotion = activePromotion("Buy A get B free", PromotionApplyMethod.AUTO);
            promotion.addBenefit(PromotionBenefit.builder().benefitType(BenefitType.FREE_PRODUCT)
                    .value(BigDecimal.ZERO).giftProductId(productB).getQuantity(1).build());
            promotion.addTarget(target(PromotionTargetType.PRODUCT, productA, false));
            activePromotions(promotion);

            assertThat(promotionApplyService.preview(request(200_000, line(productB, null, 1, 200_000)))
                    .getAvailablePromotions()).isEmpty();
        }
    }

    @Nested
    @DisplayName("condition boundaries")
    class ConditionBoundaries {

        Promotion tenPercentWith(PromotionCondition... conditions) {
            Promotion promotion = activePromotion("Conditional", PromotionApplyMethod.AUTO);
            promotion.addBenefit(percent(BigDecimal.TEN, null));
            for (PromotionCondition condition : conditions) {
                promotion.addCondition(condition);
            }
            return promotion;
        }

        @Test
        @DisplayName("every condition must hold")
        void preview_allConditionsRequired() {
            activePromotions(tenPercentWith(
                    condition(ConditionType.MIN_ORDER_AMOUNT, "100000"),
                    condition(ConditionType.MAX_ORDER_AMOUNT, "200000")));

            assertThat(promotionApplyService.preview(request(99_999)).getAvailablePromotions()).isEmpty();
            assertThat(promotionApplyService.preview(request(100_000)).getAvailablePromotions()).hasSize(1);
            assertThat(promotionApplyService.preview(request(200_000)).getAvailablePromotions()).hasSize(1);
            assertThat(promotionApplyService.preview(request(200_001)).getAvailablePromotions()).isEmpty();
        }

        @Test
        @DisplayName("amount thresholds compare decimals exactly")
        void preview_minOrderAmount_decimalBoundary() {
            activePromotions(tenPercentWith(condition(ConditionType.MIN_ORDER_AMOUNT, "{\"amount\":500000.50}")));

            assertThat(promotionApplyService.preview(requestOf("500000.49")).getAvailablePromotions()).isEmpty();
            assertThat(promotionApplyService.preview(requestOf("500000.50")).getAvailablePromotions()).hasSize(1);
        }

        @Test
        @DisplayName("a JSON object wrapped in a JSON string is unwrapped")
        void preview_minOrderAmount_jsonInsideString() {
            activePromotions(tenPercentWith(
                    condition(ConditionType.MIN_ORDER_AMOUNT, "\"{\\\"amount\\\":300000}\"")));

            assertThat(promotionApplyService.preview(request(299_999)).getAvailablePromotions()).isEmpty();
            assertThat(promotionApplyService.preview(request(300_000)).getAvailablePromotions()).hasSize(1);
        }

        @ParameterizedTest(name = "amount condition value [{0}] is rejected")
        @ValueSource(strings = {"   ", "abc", "null", "true", "[1,2]", "{\"amount\":null}", "{\"amount\":true}",
                "{\"amount\":\"abc\"}", "{}"})
        void preview_unreadableAmountCondition_throws(String conditionValue) {
            activePromotions(tenPercentWith(condition(ConditionType.MIN_ORDER_AMOUNT, conditionValue)));

            assertThatThrownBy(() -> promotionApplyService.preview(request(100_000)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Order amount condition value must contain numeric amount");
        }

        @Test
        @DisplayName("a missing amount condition value is rejected")
        void preview_nullAmountCondition_throws() {
            activePromotions(tenPercentWith(condition(ConditionType.MAX_ORDER_AMOUNT, null)));

            assertThatThrownBy(() -> promotionApplyService.preview(request(100_000)))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("MIN_ITEM_QUANTITY is inclusive, ignores lines without quantity and treats a missing cart as empty")
        void preview_minItemQuantity_boundaries() {
            activePromotions(tenPercentWith(condition(ConditionType.MIN_ITEM_QUANTITY, "3")));
            PromotionPreviewRequest withoutLines = request(100_000);
            withoutLines.setLines(null);
            PromotionPreviewLineRequest lineWithoutQuantity = PromotionPreviewLineRequest.builder()
                    .productId(productB).lineAmount(BigDecimal.valueOf(50_000)).build();

            assertThat(promotionApplyService.preview(withoutLines).getAvailablePromotions()).isEmpty();
            assertThat(promotionApplyService.preview(request(100_000, line(productA, null, 2, 50_000),
                    lineWithoutQuantity)).getAvailablePromotions()).isEmpty();
            assertThat(promotionApplyService.preview(request(100_000, line(productA, null, 3, 50_000),
                    lineWithoutQuantity)).getAvailablePromotions()).hasSize(1);
        }

        @Test
        @DisplayName("FIRST_PURCHASE is not granted when the customer's order count is unknown")
        void preview_firstPurchase_unknownOrderCount() {
            activePromotions(tenPercentWith(condition(ConditionType.FIRST_PURCHASE, "true")));
            PromotionPreviewRequest request = request(100_000);
            request.setCustomerId(UUID.randomUUID());

            assertThat(promotionApplyService.preview(request).getAvailablePromotions()).isEmpty();
        }

        @Test
        @DisplayName("CHANNEL needs a channel on the request and matches case-insensitively")
        void preview_channel_boundaries() {
            activePromotions(tenPercentWith(condition(ConditionType.CHANNEL, "[\"pos\",\"app\"]")));
            PromotionPreviewRequest withoutChannel = request(100_000);
            withoutChannel.setChannel(null);
            PromotionPreviewRequest web = request(100_000);
            web.setChannel(PromotionChannel.WEB);
            PromotionPreviewRequest app = request(100_000);
            app.setChannel(PromotionChannel.APP);

            assertThat(promotionApplyService.preview(withoutChannel).getAvailablePromotions()).isEmpty();
            assertThat(promotionApplyService.preview(web).getAvailablePromotions()).isEmpty();
            assertThat(promotionApplyService.preview(app).getAvailablePromotions()).hasSize(1);
            assertThat(promotionApplyService.preview(request(100_000)).getAvailablePromotions()).hasSize(1);
        }

        @Test
        @DisplayName("BRANCH needs a branch on the request")
        void preview_branch_missingBranch() {
            UUID branch = UUID.randomUUID();
            activePromotions(tenPercentWith(condition(ConditionType.BRANCH, "[\"" + branch + "\"]")));
            PromotionPreviewRequest atBranch = request(100_000);
            atBranch.setBranchId(branch);

            assertThat(promotionApplyService.preview(request(100_000)).getAvailablePromotions()).isEmpty();
            assertThat(promotionApplyService.preview(atBranch).getAvailablePromotions()).hasSize(1);
        }

        @Test
        @DisplayName("a COUPON_CODE promotion whose conditions fail never looks up coupons")
        void preview_failedCondition_noCouponLookup() {
            Promotion promotion = activePromotion("Coupon min 500k", PromotionApplyMethod.COUPON_CODE);
            promotion.addBenefit(percent(BigDecimal.TEN, null));
            promotion.addCondition(condition(ConditionType.MIN_ORDER_AMOUNT, "500000"));
            activePromotions(promotion);

            assertThat(promotionApplyService.preview(request(100_000)).getAvailablePromotions()).isEmpty();
            verifyNoInteractions(couponRepository, couponUsageRepository);
        }

        @Test
        @Disabled("Suspected bug: PromotionApplyServiceImpl.matchCondition never reads PromotionCondition.operator, "
                + "so a CHANNEL NOT_IN [POS] promotion is still offered on POS")
        @DisplayName("CHANNEL with operator NOT_IN excludes the listed channels")
        void preview_channelNotIn_excludesListedChannel() {
            Promotion promotion = activePromotion("Not on POS", PromotionApplyMethod.AUTO);
            promotion.addBenefit(percent(BigDecimal.TEN, null));
            promotion.addCondition(PromotionCondition.builder().conditionType(ConditionType.CHANNEL)
                    .operator(ConditionOperator.NOT_IN).conditionValue("[\"POS\"]").build());
            activePromotions(promotion);
            PromotionPreviewRequest online = request(100_000);
            online.setChannel(PromotionChannel.ONLINE);

            assertThat(promotionApplyService.preview(request(100_000)).getAvailablePromotions()).isEmpty();
            assertThat(promotionApplyService.preview(online).getAvailablePromotions()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("target boundaries")
    class TargetBoundaries {

        @Test
        @DisplayName("BRAND targets match nothing on a POS cart")
        void preview_brandTarget_skipped() {
            Promotion promotion = activePromotion("Brand", PromotionApplyMethod.AUTO);
            promotion.addBenefit(percent(BigDecimal.TEN, null));
            promotion.addTarget(target(PromotionTargetType.BRAND, productA, false));
            activePromotions(promotion);

            assertThat(promotionApplyService.preview(request(100_000, line(productA, categoryTea, 1, 100_000)))
                    .getAvailablePromotions()).isEmpty();
        }

        @Test
        @DisplayName("an exclusion wins over an inclusion of the same line")
        void preview_excludedCategoryBeatsIncludedProduct() {
            Promotion promotion = activePromotion("A and B but not tea", PromotionApplyMethod.AUTO);
            promotion.addBenefit(percent(BigDecimal.TEN, null));
            promotion.addTarget(target(PromotionTargetType.PRODUCT, productA, false));
            promotion.addTarget(target(PromotionTargetType.PRODUCT, productB, false));
            promotion.addTarget(target(PromotionTargetType.CATEGORY, categoryTea, true));
            activePromotions(promotion);

            PromotionPreviewResponse result = promotionApplyService.preview(request(300_000,
                    line(productA, categoryTea, 1, 100_000), line(productB, null, 1, 200_000)));

            assertThat(result.getAvailablePromotions().get(0).getEstimatedDiscount()).isEqualByComparingTo("20000");
        }

        @Test
        @DisplayName("a target without excluded flag is an inclusion")
        void preview_targetWithNullExcluded_isIncluded() {
            Promotion promotion = activePromotion("A only", PromotionApplyMethod.AUTO);
            promotion.addBenefit(percent(BigDecimal.TEN, null));
            PromotionTarget target = target(PromotionTargetType.PRODUCT, productA, false);
            target.setExcluded(null);
            promotion.addTarget(target);
            activePromotions(promotion);

            PromotionPreviewResponse result = promotionApplyService.preview(request(300_000,
                    line(productA, null, 1, 100_000), line(productB, null, 1, 200_000)));

            assertThat(result.getAvailablePromotions().get(0).getEstimatedDiscount()).isEqualByComparingTo("10000");
        }

        @Test
        @DisplayName("several lines of a targeted product are added up and lines without amount are ignored")
        void preview_eligibleAmount_sumsMatchingLines() {
            Promotion promotion = activePromotion("A only", PromotionApplyMethod.AUTO);
            promotion.addBenefit(percent(BigDecimal.TEN, null));
            promotion.addTarget(target(PromotionTargetType.PRODUCT, productA, false));
            activePromotions(promotion);
            PromotionPreviewLineRequest lineWithoutAmount = PromotionPreviewLineRequest.builder()
                    .productId(productA).quantity(1).build();

            PromotionPreviewResponse result = promotionApplyService.preview(request(500_000,
                    line(productA, null, 1, 100_000), line(productA, null, 2, 150_000), lineWithoutAmount,
                    line(productB, null, 1, 250_000)));

            assertThat(result.getAvailablePromotions().get(0).getEstimatedDiscount()).isEqualByComparingTo("25000");
        }

        @Test
        @DisplayName("fixed discount of a targeted promotion is limited to the targeted lines")
        void preview_fixedDiscount_cappedAtEligibleAmount() {
            Promotion promotion = activePromotion("80k off A", PromotionApplyMethod.AUTO);
            promotion.addBenefit(fixed(80_000));
            promotion.addTarget(target(PromotionTargetType.PRODUCT, productA, false));
            activePromotions(promotion);

            PromotionPreviewResponse result = promotionApplyService.preview(request(300_000,
                    line(productA, null, 1, 50_000), line(productB, null, 1, 250_000)));

            assertThat(result.getAvailablePromotions().get(0).getEstimatedDiscount()).isEqualByComparingTo("50000");
        }

        @Test
        @DisplayName("a targeted promotion is skipped when the request has no cart lines")
        void preview_targetsWithoutLines_skipped() {
            Promotion promotion = activePromotion("A only", PromotionApplyMethod.AUTO);
            promotion.addBenefit(percent(BigDecimal.TEN, null));
            promotion.addTarget(target(PromotionTargetType.PRODUCT, productA, false));
            activePromotions(promotion);
            PromotionPreviewRequest withoutLines = request(100_000);
            withoutLines.setLines(null);

            assertThat(promotionApplyService.preview(withoutLines).getAvailablePromotions()).isEmpty();
            assertThat(promotionApplyService.preview(request(100_000)).getAvailablePromotions()).isEmpty();
        }
    }

    @Nested
    @DisplayName("coupon limits")
    class CouponLimits {

        final UUID customerId = UUID.randomUUID();
        final List<CouponUsageStatus> countedStatuses = List.of(CouponUsageStatus.RESERVED, CouponUsageStatus.APPLIED);

        Promotion couponPromotion() {
            Promotion promotion = activePromotion("Coupon", PromotionApplyMethod.COUPON_CODE);
            promotion.addBenefit(percent(BigDecimal.TEN, null));
            activePromotions(promotion);
            return promotion;
        }

        Coupon coupon(Promotion promotion, String code, CouponScope scope, Integer perCustomerLimit) {
            Coupon coupon = Coupon.builder().promotion(promotion).code(code).scope(scope)
                    .status(CouponStatus.ACTIVE).perCustomerLimit(perCustomerLimit).build();
            coupon.setId(UUID.randomUUID());
            return coupon;
        }

        PromotionPreviewRequest customerRequest() {
            PromotionPreviewRequest request = request(200_000);
            request.setCustomerId(customerId);
            return request;
        }

        void eligibleCoupons(Promotion promotion, Coupon... coupons) {
            when(couponRepository.findPreviewEligibleCoupons(eq(promotion.getId()), eq(customerId), isNull(),
                    eq(CouponStatus.ACTIVE), any(LocalDateTime.class))).thenReturn(List.of(coupons));
        }

        void usedBy(Coupon coupon, long times) {
            when(couponUsageRepository.countByCouponIdAndCustomerIdAndStatusInAndIsDeletedFalse(coupon.getId(),
                    customerId, countedStatuses)).thenReturn(times);
        }

        @ParameterizedTest(name = "perCustomerLimit {0}, used {1} time(s) -> offered: {2}")
        @CsvSource({
                "1, 0, true",
                "1, 1, false",
                "2, 1, true",
                "2, 2, false",
                "2, 3, false"
        })
        void preview_perCustomerLimitBoundary(int perCustomerLimit, long used, boolean offered) {
            Promotion promotion = couponPromotion();
            Coupon coupon = coupon(promotion, "LIMITED", CouponScope.PUBLIC, perCustomerLimit);
            eligibleCoupons(promotion, coupon);
            usedBy(coupon, used);

            PromotionPreviewResponse result = promotionApplyService.preview(customerRequest());

            assertThat(result.getAvailablePromotions()).hasSize(offered ? 1 : 0);
        }

        @Test
        @DisplayName("a coupon without perCustomerLimit can be used once per customer")
        void preview_nullPerCustomerLimit_defaultsToOne() {
            Promotion promotion = couponPromotion();
            Coupon coupon = coupon(promotion, "ONCE", CouponScope.PUBLIC, null);
            eligibleCoupons(promotion, coupon);
            usedBy(coupon, 1);

            assertThat(promotionApplyService.preview(customerRequest()).getAvailablePromotions()).isEmpty();
        }

        @Test
        @DisplayName("the promotion is skipped when the customer exhausted every coupon")
        void preview_allCouponsExhausted_skipped() {
            Promotion promotion = couponPromotion();
            Coupon first = coupon(promotion, "FIRST", CouponScope.PERSONAL, 1);
            Coupon second = coupon(promotion, "SECOND", CouponScope.PUBLIC, 1);
            eligibleCoupons(promotion, first, second);
            usedBy(first, 1);
            usedBy(second, 4);

            assertThat(promotionApplyService.preview(customerRequest()).getAvailablePromotions()).isEmpty();
        }

        @Test
        @DisplayName("the first eligible coupon in repository order is offered and later ones are not counted")
        void preview_firstEligibleCouponWins() {
            Promotion promotion = couponPromotion();
            Coupon personal = coupon(promotion, "PERSONAL-1", CouponScope.PERSONAL, 1);
            Coupon shared = coupon(promotion, "PUBLIC-1", CouponScope.PUBLIC, 1);
            eligibleCoupons(promotion, personal, shared);
            usedBy(personal, 0);

            AvailablePromotionResponse offer = promotionApplyService.preview(customerRequest())
                    .getAvailablePromotions().get(0);

            assertThat(offer.getCouponId()).isEqualTo(personal.getId());
            assertThat(offer.getCouponCode()).isEqualTo("PERSONAL-1");
            assertThat(offer.getCouponScope()).isEqualTo(CouponScope.PERSONAL);
            assertThat(offer.getCouponExpiresAt()).isNull();
            verify(couponUsageRepository, never()).countByCouponIdAndCustomerIdAndStatusInAndIsDeletedFalse(
                    shared.getId(), customerId, countedStatuses);
        }

        @Test
        @DisplayName("a blank coupon code searches every coupon of the promotion")
        void preview_blankCouponCode_treatedAsNoCode() {
            Promotion promotion = couponPromotion();
            Coupon coupon = coupon(promotion, "ANY", CouponScope.PUBLIC, 1);
            eligibleCoupons(promotion, coupon);
            usedBy(coupon, 0);
            PromotionPreviewRequest request = customerRequest();
            request.setCouponCode("   ");

            assertThat(promotionApplyService.preview(request).getAvailablePromotions()).hasSize(1);
        }

        @Test
        @DisplayName("each COUPON_CODE promotion is matched against its own coupons")
        void preview_severalCouponPromotions_lookedUpSeparately() {
            Promotion withCoupon = activePromotion("With coupon", PromotionApplyMethod.COUPON_CODE);
            withCoupon.addBenefit(fixed(10_000));
            Promotion withoutCoupon = activePromotion("Without coupon", PromotionApplyMethod.COUPON_CODE);
            withoutCoupon.addBenefit(fixed(20_000));
            activePromotions(withCoupon, withoutCoupon);
            Coupon coupon = coupon(withCoupon, "MINE", CouponScope.PUBLIC, 1);
            eligibleCoupons(withCoupon, coupon);
            eligibleCoupons(withoutCoupon);
            usedBy(coupon, 0);

            PromotionPreviewResponse result = promotionApplyService.preview(customerRequest());

            assertThat(result.getAvailablePromotions()).extracting(AvailablePromotionResponse::getPromotionName)
                    .containsExactly("With coupon");
        }
    }
}
