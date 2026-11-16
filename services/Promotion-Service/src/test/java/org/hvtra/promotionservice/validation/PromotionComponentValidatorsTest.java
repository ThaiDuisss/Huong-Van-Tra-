package org.hvtra.promotionservice.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hvtra.commonservice.common_core.exception.BusinessException;
import org.hvtra.promotionservice.dto.request.promotionbenefit.PromotionBenefitCreateRequest;
import org.hvtra.promotionservice.dto.request.promotionbenefit.PromotionBenefitUpdateRequest;
import org.hvtra.promotionservice.dto.request.promotioncondition.PromotionConditionCreateRequest;
import org.hvtra.promotionservice.dto.request.promotioncondition.PromotionConditionUpdateRequest;
import org.hvtra.promotionservice.dto.request.promotiontarget.PromotionTargetCreateRequest;
import org.hvtra.promotionservice.dto.request.promotiontarget.PromotionTargetUpdateRequest;
import org.hvtra.promotionservice.entity.Promotion;
import org.hvtra.promotionservice.entity.coupon.CouponUsage;
import org.hvtra.promotionservice.enums.benefit.BenefitType;
import org.hvtra.promotionservice.enums.condition.ConditionOperator;
import org.hvtra.promotionservice.enums.condition.ConditionType;
import org.hvtra.promotionservice.enums.coupon.CouponUsageStatus;
import org.hvtra.promotionservice.enums.promotion.PromotionStatus;
import org.hvtra.promotionservice.enums.promotion.PromotionTargetType;
import org.hvtra.promotionservice.repository.promotion.PromotionTargetRepository;
import org.hvtra.promotionservice.validation.validator.CouponUsageValidator;
import org.hvtra.promotionservice.validation.validator.PromotionBenefitValidator;
import org.hvtra.promotionservice.validation.validator.PromotionConditionValidator;
import org.hvtra.promotionservice.validation.validator.PromotionTargetValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Promotion component validators")
class PromotionComponentValidatorsTest {

    @Mock
    PromotionTargetRepository promotionTargetRepository;

    final PromotionBenefitValidator benefitValidator = new PromotionBenefitValidator();
    final PromotionConditionValidator conditionValidator = new PromotionConditionValidator(new ObjectMapper());
    final CouponUsageValidator usageValidator = new CouponUsageValidator();

    Promotion promotion(PromotionStatus status) {
        Promotion promotion = Promotion.builder().status(status).build();
        promotion.setId(UUID.randomUUID());
        return promotion;
    }

    @Nested
    @DisplayName("PromotionBenefitValidator")
    class Benefit {

        PromotionBenefitCreateRequest.PromotionBenefitCreateRequestBuilder benefit(BenefitType type, long value) {
            return PromotionBenefitCreateRequest.builder().benefitType(type).value(BigDecimal.valueOf(value));
        }

        @ParameterizedTest(name = "benefits of a {0} promotion are locked")
        @EnumSource(value = PromotionStatus.class, names = {"ACTIVE", "CANCELLED", "EXPIRED", "ENDED"})
        void lockedPromotion_rejects(PromotionStatus status) {
            PromotionBenefitCreateRequest request = benefit(BenefitType.FIXED_DISCOUNT, 1000).build();

            assertThatThrownBy(() -> benefitValidator.validateBeforeCreate(promotion(status), request))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Cannot modify benefits");
            assertThatThrownBy(() -> benefitValidator.validateBeforeDelete(promotion(status)))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("percent discount must be within (0, 100]")
        void percentRange() {
            assertThatThrownBy(() -> benefitValidator.validateBeforeCreate(promotion(PromotionStatus.DRAFT),
                    benefit(BenefitType.PERCENT_DISCOUNT, 0).build())).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> benefitValidator.validateBeforeCreate(promotion(PromotionStatus.DRAFT),
                    benefit(BenefitType.PERCENT_DISCOUNT, 101).build())).isInstanceOf(BusinessException.class);
            assertThatCode(() -> benefitValidator.validateBeforeCreate(promotion(PromotionStatus.DRAFT),
                    benefit(BenefitType.PERCENT_DISCOUNT, 100).build())).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("fixed discount must be positive")
        void fixedPositive() {
            assertThatThrownBy(() -> benefitValidator.validateBeforeCreate(promotion(PromotionStatus.SCHEDULED),
                    benefit(BenefitType.FIXED_DISCOUNT, 0).build()))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Fixed discount");
            assertThatCode(() -> benefitValidator.validateBeforeCreate(promotion(PromotionStatus.SCHEDULED),
                    benefit(BenefitType.FIXED_DISCOUNT, 1).build())).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("free product needs a gift product and a quantity of at least 1")
        void freeProductConfig() {
            assertThatThrownBy(() -> benefitValidator.validateBeforeCreate(promotion(PromotionStatus.DRAFT),
                    benefit(BenefitType.FREE_PRODUCT, 0).getQuantity(1).build()))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Gift product ID");
            assertThatThrownBy(() -> benefitValidator.validateBeforeCreate(promotion(PromotionStatus.DRAFT),
                    benefit(BenefitType.FREE_PRODUCT, 0).giftProductId(UUID.randomUUID()).getQuantity(0).build()))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Get quantity");
            assertThatCode(() -> benefitValidator.validateBeforeCreate(promotion(PromotionStatus.DRAFT),
                    benefit(BenefitType.FREE_PRODUCT, 0).giftProductId(UUID.randomUUID()).getQuantity(1).build()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("buy X get Y needs gift product, buy quantity and get quantity")
        void buyXGetYConfig() {
            UUID gift = UUID.randomUUID();
            assertThatThrownBy(() -> benefitValidator.validateBeforeCreate(promotion(PromotionStatus.DRAFT),
                    benefit(BenefitType.BUY_X_GET_Y, 0).buyQuantity(2).getQuantity(1).build()))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Gift product ID");
            assertThatThrownBy(() -> benefitValidator.validateBeforeCreate(promotion(PromotionStatus.DRAFT),
                    benefit(BenefitType.BUY_X_GET_Y, 0).giftProductId(gift).getQuantity(1).build()))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Buy quantity");
            assertThatThrownBy(() -> benefitValidator.validateBeforeCreate(promotion(PromotionStatus.DRAFT),
                    benefit(BenefitType.BUY_X_GET_Y, 0).giftProductId(gift).buyQuantity(2).build()))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Get quantity");
            assertThatCode(() -> benefitValidator.validateBeforeCreate(promotion(PromotionStatus.DRAFT),
                    benefit(BenefitType.BUY_X_GET_Y, 0).giftProductId(gift).buyQuantity(2).getQuantity(1).build()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("free shipping rejects a negative max discount; update applies the same rules")
        void freeShippingAndUpdate() {
            assertThatThrownBy(() -> benefitValidator.validateBeforeCreate(promotion(PromotionStatus.DRAFT),
                    benefit(BenefitType.FREE_SHIPPING, 0).maxDiscount(BigDecimal.valueOf(-1)).build()))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Max discount");
            assertThatThrownBy(() -> benefitValidator.validateBeforeUpdate(promotion(PromotionStatus.PAUSED),
                    PromotionBenefitUpdateRequest.builder().benefitType(BenefitType.PERCENT_DISCOUNT)
                            .value(BigDecimal.valueOf(150)).build()))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Percent discount");
            assertThatCode(() -> benefitValidator.validateBeforeUpdate(promotion(PromotionStatus.PAUSED),
                    PromotionBenefitUpdateRequest.builder().benefitType(BenefitType.FREE_SHIPPING)
                            .value(BigDecimal.ZERO).build())).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("PromotionConditionValidator")
    class Condition {

        @Test
        @DisplayName("condition value must be valid JSON")
        void jsonValue() {
            PromotionConditionCreateRequest bad = PromotionConditionCreateRequest.builder()
                    .conditionType(ConditionType.MIN_ORDER_AMOUNT).operator(ConditionOperator.GTE)
                    .conditionValue("{amount:}").build();
            PromotionConditionCreateRequest good = PromotionConditionCreateRequest.builder()
                    .conditionType(ConditionType.MIN_ORDER_AMOUNT).operator(ConditionOperator.GTE)
                    .conditionValue("{\"amount\":500000}").build();

            assertThatThrownBy(() -> conditionValidator.validateBeforeCreate(promotion(PromotionStatus.DRAFT), bad))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("valid JSON");
            assertThatCode(() -> conditionValidator.validateBeforeCreate(promotion(PromotionStatus.DRAFT), good))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "conditions of a {0} promotion are locked")
        @EnumSource(value = PromotionStatus.class, names = {"ACTIVE", "CANCELLED", "EXPIRED", "ENDED"})
        void lockedPromotion_rejects(PromotionStatus status) {
            PromotionConditionUpdateRequest request = PromotionConditionUpdateRequest.builder()
                    .conditionType(ConditionType.CHANNEL).operator(ConditionOperator.IN)
                    .conditionValue("{\"channels\":[\"POS\"]}").build();

            assertThatThrownBy(() -> conditionValidator.validateBeforeUpdate(promotion(status), request))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Cannot modify conditions");
            assertThatThrownBy(() -> conditionValidator.validateBeforeDelete(promotion(status)))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("editable statuses pass")
        void editableStatuses() {
            assertThatCode(() -> conditionValidator.validateBeforeDelete(promotion(PromotionStatus.DRAFT))).doesNotThrowAnyException();
            assertThatCode(() -> conditionValidator.validateBeforeDelete(promotion(PromotionStatus.SCHEDULED))).doesNotThrowAnyException();
            assertThatCode(() -> conditionValidator.validateBeforeDelete(promotion(PromotionStatus.PAUSED))).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("PromotionTargetValidator")
    class Target {

        PromotionTargetValidator targetValidator() {
            return new PromotionTargetValidator(promotionTargetRepository);
        }

        @Test
        @DisplayName("duplicate target of the same promotion is rejected")
        void duplicateTarget() {
            Promotion promotion = promotion(PromotionStatus.DRAFT);
            UUID productId = UUID.randomUUID();
            PromotionTargetCreateRequest request = PromotionTargetCreateRequest.builder()
                    .targetType(PromotionTargetType.PRODUCT).targetId(productId).build();
            when(promotionTargetRepository.existsByPromotionIdAndTargetTypeAndTargetIdAndIsDeletedFalse(
                    promotion.getId(), PromotionTargetType.PRODUCT, productId)).thenReturn(true, false);

            assertThatThrownBy(() -> targetValidator().validateBeforeCreate(promotion, request))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("already exists");
            assertThatCode(() -> targetValidator().validateBeforeCreate(promotion, request)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("update ignores the target being edited when checking duplicates")
        void duplicateOnUpdate() {
            Promotion promotion = promotion(PromotionStatus.DRAFT);
            UUID targetId = UUID.randomUUID();
            UUID categoryId = UUID.randomUUID();
            PromotionTargetUpdateRequest request = PromotionTargetUpdateRequest.builder()
                    .targetType(PromotionTargetType.CATEGORY).targetId(categoryId).build();
            when(promotionTargetRepository.existsByPromotionIdAndTargetTypeAndTargetIdAndIdNotAndIsDeletedFalse(
                    promotion.getId(), PromotionTargetType.CATEGORY, categoryId, targetId)).thenReturn(true);

            assertThatThrownBy(() -> targetValidator().validateBeforeUpdate(promotion, targetId, request))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("override value needs an override type, and the type needs a value")
        void overrideConfig() {
            Promotion promotion = promotion(PromotionStatus.DRAFT);
            when(promotionTargetRepository.existsByPromotionIdAndTargetTypeAndTargetIdAndIsDeletedFalse(
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any())).thenReturn(false);

            assertThatThrownBy(() -> targetValidator().validateBeforeCreate(promotion, PromotionTargetCreateRequest.builder()
                    .targetType(PromotionTargetType.PRODUCT).targetId(UUID.randomUUID())
                    .overrideValue(BigDecimal.TEN).build()))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Override benefit type is required");
            assertThatThrownBy(() -> targetValidator().validateBeforeCreate(promotion, PromotionTargetCreateRequest.builder()
                    .targetType(PromotionTargetType.PRODUCT).targetId(UUID.randomUUID())
                    .overrideBenefitType(BenefitType.FIXED_DISCOUNT).build()))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Override value is required");
            assertThatThrownBy(() -> targetValidator().validateBeforeCreate(promotion, PromotionTargetCreateRequest.builder()
                    .targetType(PromotionTargetType.PRODUCT).targetId(UUID.randomUUID())
                    .overrideBenefitType(BenefitType.PERCENT_DISCOUNT).overrideValue(BigDecimal.valueOf(120)).build()))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("must not exceed 100");
            assertThatCode(() -> targetValidator().validateBeforeCreate(promotion, PromotionTargetCreateRequest.builder()
                    .targetType(PromotionTargetType.PRODUCT).targetId(UUID.randomUUID())
                    .overrideBenefitType(BenefitType.PERCENT_DISCOUNT).overrideValue(BigDecimal.valueOf(15)).build()))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "targets of a {0} promotion are locked")
        @EnumSource(value = PromotionStatus.class, names = {"ACTIVE", "CANCELLED", "EXPIRED", "ENDED"})
        void lockedPromotion_rejects(PromotionStatus status) {
            assertThatThrownBy(() -> targetValidator().validateBeforeDelete(promotion(status)))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Cannot modify targets");
        }
    }

    @Nested
    @DisplayName("CouponUsageValidator")
    class Usage {

        CouponUsage usage(CouponUsageStatus status) {
            return CouponUsage.builder().status(status).build();
        }

        @Test
        @DisplayName("apply, release and expire accept only RESERVED usages")
        void reservedOnly() {
            assertThatCode(() -> usageValidator.validateBeforeApply(usage(CouponUsageStatus.RESERVED))).doesNotThrowAnyException();
            assertThatCode(() -> usageValidator.validateBeforeRelease(usage(CouponUsageStatus.RESERVED))).doesNotThrowAnyException();
            assertThatCode(() -> usageValidator.validateBeforeExpire(usage(CouponUsageStatus.RESERVED))).doesNotThrowAnyException();
            assertThatThrownBy(() -> usageValidator.validateBeforeApply(usage(CouponUsageStatus.RELEASED)))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Only reserved");
            assertThatThrownBy(() -> usageValidator.validateBeforeRelease(usage(CouponUsageStatus.APPLIED)))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Only reserved");
            assertThatThrownBy(() -> usageValidator.validateBeforeExpire(usage(CouponUsageStatus.CANCELLED)))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Only reserved");
        }

        @Test
        @DisplayName("cancel rejects CANCELLED and APPLIED usages, accepts RESERVED and RELEASED")
        void cancelRules() {
            assertThatThrownBy(() -> usageValidator.validateBeforeCancel(usage(CouponUsageStatus.CANCELLED)))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("already cancelled");
            assertThatThrownBy(() -> usageValidator.validateBeforeCancel(usage(CouponUsageStatus.APPLIED)))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Applied coupon usage");
            assertThatCode(() -> usageValidator.validateBeforeCancel(usage(CouponUsageStatus.RESERVED))).doesNotThrowAnyException();
            assertThatCode(() -> usageValidator.validateBeforeCancel(usage(CouponUsageStatus.RELEASED))).doesNotThrowAnyException();
        }
    }
}
