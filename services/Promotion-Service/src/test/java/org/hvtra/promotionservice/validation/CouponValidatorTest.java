package org.hvtra.promotionservice.validation;

import org.hvtra.commonservice.common_core.exception.BusinessException;
import org.hvtra.promotionservice.dto.request.coupon.CouponBulkGenerateRequest;
import org.hvtra.promotionservice.dto.request.coupon.CouponCreateRequest;
import org.hvtra.promotionservice.dto.request.coupon.CouponGeneratePersonalRequest;
import org.hvtra.promotionservice.dto.request.coupon.CouponGeneratePublicRequest;
import org.hvtra.promotionservice.dto.request.coupon.CouponStatusUpdateRequest;
import org.hvtra.promotionservice.dto.request.coupon.CouponUpdateRequest;
import org.hvtra.promotionservice.dto.request.couponusage.CouponUsageReserveRequest;
import org.hvtra.promotionservice.entity.Promotion;
import org.hvtra.promotionservice.entity.coupon.Coupon;
import org.hvtra.promotionservice.enums.coupon.CouponScope;
import org.hvtra.promotionservice.enums.coupon.CouponStatus;
import org.hvtra.promotionservice.enums.promotion.PromotionStatus;
import org.hvtra.promotionservice.repository.coupon.CouponRepository;
import org.hvtra.promotionservice.validation.validator.CouponValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponValidator")
class CouponValidatorTest {

    @Mock
    CouponRepository couponRepository;

    @InjectMocks
    CouponValidator validator;

    Promotion promotion(PromotionStatus status) {
        return Promotion.builder().status(status).build();
    }

    Coupon coupon(CouponStatus status) {
        return Coupon.builder().code("TET2027").status(status).scope(CouponScope.PUBLIC)
                .promotion(promotion(PromotionStatus.ACTIVE)).usageLimit(5).usedCount(0).build();
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("validateCreateRequest needs a promotion id")
        void validateCreateRequest_requiresPromotionId() {
            assertThatThrownBy(() -> validator.validateCreateRequest(CouponCreateRequest.builder().build()))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Promotion ID is required");
            assertThatCode(() -> validator.validateCreateRequest(
                    CouponCreateRequest.builder().promotionId(UUID.randomUUID()).build())).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "promotion {0} cannot receive coupons")
        @EnumSource(value = PromotionStatus.class, names = {"CANCELLED", "EXPIRED", "ENDED"})
        void validateBeforeCreate_closedPromotion_rejects(PromotionStatus status) {
            CouponCreateRequest request = CouponCreateRequest.builder().scope(CouponScope.PUBLIC).code("X").build();

            assertThatThrownBy(() -> validator.validateBeforeCreate(promotion(status), request))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Cannot create coupon");
        }

        @Test
        @DisplayName("PERSONAL scope requires a customer and PUBLIC scope forbids one")
        void validateBeforeCreate_scopeAndCustomer() {
            CouponCreateRequest personal = CouponCreateRequest.builder().scope(CouponScope.PERSONAL).code("A").build();
            CouponCreateRequest publicWithCustomer = CouponCreateRequest.builder().scope(CouponScope.PUBLIC)
                    .customerId(UUID.randomUUID()).code("B").build();

            assertThatThrownBy(() -> validator.validateBeforeCreate(promotion(PromotionStatus.DRAFT), personal))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Customer ID is required");
            assertThatThrownBy(() -> validator.validateBeforeCreate(promotion(PromotionStatus.DRAFT), publicWithCustomer))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("must be null");
            verify(couponRepository, never()).existsByCodeAndIsDeletedFalse(anyString());
        }

        @Test
        @DisplayName("perCustomerLimit above usageLimit is rejected")
        void validateBeforeCreate_perCustomerAboveUsage_rejects() {
            CouponCreateRequest request = CouponCreateRequest.builder().scope(CouponScope.PUBLIC).code("A")
                    .usageLimit(1).perCustomerLimit(2).build();

            assertThatThrownBy(() -> validator.validateBeforeCreate(promotion(PromotionStatus.ACTIVE), request))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Per customer limit");
        }

        @Test
        @DisplayName("duplicate code is rejected, unique code passes")
        void validateBeforeCreate_uniqueCode() {
            CouponCreateRequest request = CouponCreateRequest.builder().scope(CouponScope.PUBLIC).code("TET2027")
                    .usageLimit(10).perCustomerLimit(1).build();
            when(couponRepository.existsByCodeAndIsDeletedFalse("TET2027")).thenReturn(true, false);

            assertThatThrownBy(() -> validator.validateBeforeCreate(promotion(PromotionStatus.ACTIVE), request))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("already exists");
            assertThatCode(() -> validator.validateBeforeCreate(promotion(PromotionStatus.ACTIVE), request))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("update and status")
    class UpdateAndStatus {

        @Test
        @DisplayName("update keeps the same code without a uniqueness check")
        void validateBeforeUpdate_sameCode_skipsUniqueness() {
            Coupon coupon = coupon(CouponStatus.DRAFT);
            CouponUpdateRequest request = CouponUpdateRequest.builder().code("TET2027").scope(CouponScope.PUBLIC).build();

            assertThatCode(() -> validator.validateBeforeUpdate(coupon, request)).doesNotThrowAnyException();
            verify(couponRepository, never()).existsByCodeAndIsDeletedFalse(anyString());
        }

        @Test
        @DisplayName("update to a code used elsewhere is rejected")
        void validateBeforeUpdate_newDuplicateCode_rejects() {
            Coupon coupon = coupon(CouponStatus.DRAFT);
            CouponUpdateRequest request = CouponUpdateRequest.builder().code("NEW").scope(CouponScope.PUBLIC).build();
            when(couponRepository.existsByCodeAndIsDeletedFalse("NEW")).thenReturn(true);

            assertThatThrownBy(() -> validator.validateBeforeUpdate(coupon, request))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("already exists");
        }

        @ParameterizedTest(name = "{0} coupon cannot be updated")
        @EnumSource(value = CouponStatus.class, names = {"CANCELLED", "ARCHIVED"})
        void validateBeforeUpdate_closedCoupon_rejects(CouponStatus status) {
            CouponUpdateRequest request = CouponUpdateRequest.builder().code("TET2027").scope(CouponScope.PUBLIC).build();

            assertThatThrownBy(() -> validator.validateBeforeUpdate(coupon(status), request))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("cannot be updated");
        }

        @Test
        @DisplayName("status transition rejects same status and closed states")
        void validateStatusTransition() {
            CouponStatusUpdateRequest toActive = CouponStatusUpdateRequest.builder().status(CouponStatus.ACTIVE).build();

            assertThatThrownBy(() -> validator.validateStatusTransition(coupon(CouponStatus.ACTIVE), toActive))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("already in status");
            assertThatThrownBy(() -> validator.validateStatusTransition(coupon(CouponStatus.CANCELLED), toActive))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Cancelled coupon");
            assertThatThrownBy(() -> validator.validateStatusTransition(coupon(CouponStatus.ARCHIVED), toActive))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Archived coupon");
            assertThatCode(() -> validator.validateStatusTransition(coupon(CouponStatus.PAUSED), toActive))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("only non-active coupons can be deleted")
        void validateBeforeDelete() {
            assertThatThrownBy(() -> validator.validateBeforeDelete(coupon(CouponStatus.ACTIVE)))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Active coupon cannot be deleted");
            assertThatCode(() -> validator.validateBeforeDelete(coupon(CouponStatus.PAUSED))).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("lifecycle guards")
    class LifecycleGuards {

        @ParameterizedTest(name = "activate from {0} is rejected")
        @EnumSource(value = CouponStatus.class, names = {"ACTIVE", "CANCELLED", "ARCHIVED", "EXPIRED"})
        void validateBeforeActivate_rejects(CouponStatus status) {
            assertThatThrownBy(() -> validator.validateBeforeActivate(coupon(status))).isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("activate rejects a coupon whose expiresAt is in the past and accepts a DRAFT one")
        void validateBeforeActivate_expiry() {
            Coupon expired = coupon(CouponStatus.DRAFT);
            expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
            Coupon valid = coupon(CouponStatus.SCHEDULED);
            valid.setExpiresAt(LocalDateTime.now().plusDays(1));

            assertThatThrownBy(() -> validator.validateBeforeActivate(expired))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Expired coupon");
            assertThatCode(() -> validator.validateBeforeActivate(valid)).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "pause from {0} is rejected")
        @EnumSource(value = CouponStatus.class, names = {"PAUSED", "CANCELLED", "ARCHIVED", "EXPIRED"})
        void validateBeforePause_rejects(CouponStatus status) {
            assertThatThrownBy(() -> validator.validateBeforePause(coupon(status))).isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("pause, cancel, expire and archive accept the expected source states")
        void lifecycle_acceptsExpectedStates() {
            assertThatCode(() -> validator.validateBeforePause(coupon(CouponStatus.ACTIVE))).doesNotThrowAnyException();
            assertThatCode(() -> validator.validateBeforeCancel(coupon(CouponStatus.ACTIVE))).doesNotThrowAnyException();
            assertThatCode(() -> validator.validateBeforeExpire(coupon(CouponStatus.ACTIVE))).doesNotThrowAnyException();
            assertThatCode(() -> validator.validateBeforeArchive(coupon(CouponStatus.PAUSED))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("cancel rejects CANCELLED and ARCHIVED coupons")
        void validateBeforeCancel_rejects() {
            assertThatThrownBy(() -> validator.validateBeforeCancel(coupon(CouponStatus.CANCELLED)))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("already cancelled");
            assertThatThrownBy(() -> validator.validateBeforeCancel(coupon(CouponStatus.ARCHIVED)))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Archived");
        }

        @Test
        @DisplayName("archive rejects ARCHIVED and ACTIVE coupons")
        void validateBeforeArchive_rejects() {
            assertThatThrownBy(() -> validator.validateBeforeArchive(coupon(CouponStatus.ARCHIVED)))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("already archived");
            assertThatThrownBy(() -> validator.validateBeforeArchive(coupon(CouponStatus.ACTIVE)))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Active coupon cannot be archived");
        }

        @ParameterizedTest(name = "expire from {0} is rejected")
        @EnumSource(value = CouponStatus.class, names = {"EXPIRED", "CANCELLED", "ARCHIVED"})
        void validateBeforeExpire_rejects(CouponStatus status) {
            assertThatThrownBy(() -> validator.validateBeforeExpire(coupon(status))).isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("reserve")
    class Reserve {

        CouponUsageReserveRequest request(UUID customerId) {
            return CouponUsageReserveRequest.builder().couponCode("TET2027").orderId(UUID.randomUUID())
                    .customerId(customerId).discountAmount(BigDecimal.TEN).build();
        }

        @Test
        @DisplayName("only ACTIVE coupons of ACTIVE promotions can be reserved")
        void validateBeforeReserve_statusChecks() {
            Coupon draft = coupon(CouponStatus.DRAFT);
            Coupon pausedPromotion = coupon(CouponStatus.ACTIVE);
            pausedPromotion.setPromotion(promotion(PromotionStatus.PAUSED));
            Coupon noPromotion = coupon(CouponStatus.ACTIVE);
            noPromotion.setPromotion(null);

            assertThatThrownBy(() -> validator.validateBeforeReserve(draft, request(UUID.randomUUID())))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Only active coupon");
            assertThatThrownBy(() -> validator.validateBeforeReserve(pausedPromotion, request(UUID.randomUUID())))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("promotion is not active");
            assertThatThrownBy(() -> validator.validateBeforeReserve(noPromotion, request(UUID.randomUUID())))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("promotion is not active");
        }

        @Test
        @DisplayName("expired coupon and exhausted usage limit are rejected")
        void validateBeforeReserve_expiryAndLimit() {
            Coupon expired = coupon(CouponStatus.ACTIVE);
            expired.setExpiresAt(LocalDateTime.now().minusSeconds(1));
            Coupon exhausted = coupon(CouponStatus.ACTIVE);
            exhausted.setUsageLimit(2);
            exhausted.setUsedCount(2);
            Coupon nullLimits = coupon(CouponStatus.ACTIVE);
            nullLimits.setUsageLimit(null);
            nullLimits.setUsedCount(1);

            assertThatThrownBy(() -> validator.validateBeforeReserve(expired, request(UUID.randomUUID())))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Expired coupon");
            assertThatThrownBy(() -> validator.validateBeforeReserve(exhausted, request(UUID.randomUUID())))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("usage limit");
            assertThatThrownBy(() -> validator.validateBeforeReserve(nullLimits, request(UUID.randomUUID())))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("usage limit");
        }

        @Test
        @DisplayName("PERSONAL coupon only works for its own customer")
        void validateBeforeReserve_personalCustomer() {
            UUID owner = UUID.randomUUID();
            Coupon personal = coupon(CouponStatus.ACTIVE);
            personal.setScope(CouponScope.PERSONAL);
            personal.setCustomerId(owner);

            assertThatThrownBy(() -> validator.validateBeforeReserve(personal, request(UUID.randomUUID())))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("does not belong");
            assertThatCode(() -> validator.validateBeforeReserve(personal, request(owner))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("valid public coupon with remaining quota passes")
        void validateBeforeReserve_valid() {
            Coupon coupon = coupon(CouponStatus.ACTIVE);
            coupon.setExpiresAt(LocalDateTime.now().plusDays(1));

            assertThatCode(() -> validator.validateBeforeReserve(coupon, request(UUID.randomUUID()))).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("generation")
    class Generation {

        @Test
        @DisplayName("public generation rejects usageLimit below 1 and perCustomerLimit above usageLimit")
        void validateGeneratePublic() {
            assertThatThrownBy(() -> validator.validateGeneratePublic(
                    CouponGeneratePublicRequest.builder().usageLimit(0).build()))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Usage limit");
            assertThatThrownBy(() -> validator.validateGeneratePublic(
                    CouponGeneratePublicRequest.builder().usageLimit(1).perCustomerLimit(3).build()))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Per customer limit");
            assertThatCode(() -> validator.validateGeneratePublic(
                    CouponGeneratePublicRequest.builder().usageLimit(3).perCustomerLimit(3).build()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("personal generation requires a customer id")
        void validateGeneratePersonal() {
            assertThatThrownBy(() -> validator.validateGeneratePersonal(CouponGeneratePersonalRequest.builder().build()))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Customer ID is required");
            assertThatCode(() -> validator.validateGeneratePersonal(
                    CouponGeneratePersonalRequest.builder().customerId(UUID.randomUUID()).build()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("bulk generation needs a quantity for PUBLIC and customer ids for PERSONAL")
        void validateBulkGenerate() {
            assertThatThrownBy(() -> validator.validateBulkGenerate(
                    CouponBulkGenerateRequest.builder().scope(CouponScope.PUBLIC).quantity(0).build()))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Quantity is required");
            assertThatThrownBy(() -> validator.validateBulkGenerate(
                    CouponBulkGenerateRequest.builder().scope(CouponScope.PERSONAL).customerIds(List.of()).build()))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Customer IDs are required");
            assertThatCode(() -> validator.validateBulkGenerate(
                    CouponBulkGenerateRequest.builder().scope(CouponScope.PUBLIC).quantity(5).build()))
                    .doesNotThrowAnyException();
            assertThatCode(() -> validator.validateBulkGenerate(
                    CouponBulkGenerateRequest.builder().scope(CouponScope.PERSONAL)
                            .customerIds(List.of(UUID.randomUUID())).build()))
                    .doesNotThrowAnyException();
        }
    }
}
