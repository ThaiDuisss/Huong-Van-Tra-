package org.hvtra.promotionservice.service;

import org.hvtra.commonservice.common_core.exception.BusinessException;
import org.hvtra.promotionservice.dto.request.couponusage.CouponUsageReserveRequest;
import org.hvtra.promotionservice.dto.response.CouponUsageResponse;
import org.hvtra.promotionservice.entity.Promotion;
import org.hvtra.promotionservice.entity.coupon.Coupon;
import org.hvtra.promotionservice.entity.coupon.CouponUsage;
import org.hvtra.promotionservice.enums.coupon.CouponScope;
import org.hvtra.promotionservice.enums.coupon.CouponStatus;
import org.hvtra.promotionservice.enums.coupon.CouponUsageStatus;
import org.hvtra.promotionservice.enums.promotion.PromotionChannel;
import org.hvtra.promotionservice.enums.promotion.PromotionStatus;
import org.hvtra.promotionservice.mapper.CouponUsageMapperImpl;
import org.hvtra.promotionservice.repository.coupon.CouponRepository;
import org.hvtra.promotionservice.repository.coupon.CouponUsageRepository;
import org.hvtra.promotionservice.service.impl.CouponUsageServiceImpl;
import org.hvtra.promotionservice.validation.validator.CouponUsageValidator;
import org.hvtra.promotionservice.validation.validator.CouponValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * Coupon reservation lifecycle with the real validators and mapper; only the repositories are replaced
 * by an in-memory list of usages.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Coupon reservation flow (service + real validators)")
class CouponReservationFlowTest {

    static final String CODE = "TET2027";

    @Mock
    CouponRepository couponRepository;

    @Mock
    CouponUsageRepository couponUsageRepository;

    CouponUsageServiceImpl couponUsageService;

    final List<CouponUsage> storedUsages = new ArrayList<>();
    Promotion promotion;
    Coupon coupon;

    @BeforeEach
    void setUp() {
        couponUsageService = new CouponUsageServiceImpl(couponUsageRepository, couponRepository,
                new CouponUsageMapperImpl(), new CouponUsageValidator(), new CouponValidator(couponRepository));

        promotion = Promotion.builder().name("Tet").status(PromotionStatus.ACTIVE).build();
        coupon = Coupon.builder().promotion(promotion).code(CODE).scope(CouponScope.PUBLIC)
                .status(CouponStatus.ACTIVE).usageLimit(2).usedCount(0).perCustomerLimit(1).build();
        coupon.setId(UUID.randomUUID());

        lenient().when(couponRepository.findByCodeAndIsDeletedFalseForUpdate(CODE)).thenReturn(Optional.of(coupon));
        lenient().when(couponRepository.save(any(Coupon.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(couponUsageRepository.save(any(CouponUsage.class))).thenAnswer(invocation -> {
            CouponUsage usage = invocation.getArgument(0);
            if (usage.getId() == null) {
                usage.setId(UUID.randomUUID());
                storedUsages.add(usage);
            }
            return usage;
        });
        lenient().when(couponUsageRepository.findByIdAndIsDeletedFalse(any(UUID.class))).thenAnswer(invocation ->
                storedUsages.stream().filter(usage -> usage.getId().equals(invocation.getArgument(0))).findFirst());
        lenient().when(couponUsageRepository.findByCouponIdAndOrderIdAndStatusAndIsDeletedFalse(
                any(UUID.class), any(UUID.class), any(CouponUsageStatus.class))).thenAnswer(invocation ->
                storedUsages.stream()
                        .filter(usage -> usage.getCoupon().getId().equals(invocation.getArgument(0)))
                        .filter(usage -> usage.getOrderId().equals(invocation.getArgument(1)))
                        .filter(usage -> usage.getStatus() == invocation.getArgument(2))
                        .findFirst());
        lenient().when(couponUsageRepository.countByCouponIdAndCustomerIdAndStatusInAndIsDeletedFalse(
                any(UUID.class), any(UUID.class), any())).thenAnswer(invocation -> {
            Collection<CouponUsageStatus> statuses = invocation.getArgument(2);
            return storedUsages.stream()
                    .filter(usage -> usage.getCoupon().getId().equals(invocation.getArgument(0)))
                    .filter(usage -> invocation.getArgument(1).equals(usage.getCustomerId()))
                    .filter(usage -> statuses.contains(usage.getStatus()))
                    .count();
        });
    }

    CouponUsageResponse reserve(UUID customerId, UUID orderId) {
        return couponUsageService.reserveCoupon(CouponUsageReserveRequest.builder().couponCode(CODE)
                .orderId(orderId).customerId(customerId).discountAmount(BigDecimal.valueOf(50_000))
                .channel(PromotionChannel.POS).build());
    }

    CouponUsageResponse reserve(UUID customerId) {
        return reserve(customerId, UUID.randomUUID());
    }

    @Nested
    @DisplayName("global usage limit")
    class GlobalLimit {

        @Test
        @DisplayName("reservations consume the quota until the usage limit is reached")
        void reserve_untilUsageLimit() {
            CouponUsageResponse first = reserve(UUID.randomUUID());
            CouponUsageResponse second = reserve(UUID.randomUUID());

            assertThat(first.getStatus()).isEqualTo(CouponUsageStatus.RESERVED);
            assertThat(first.getCouponCode()).isEqualTo(CODE);
            assertThat(first.getCouponId()).isEqualTo(coupon.getId());
            assertThat(first.getDiscountAmount()).isEqualByComparingTo("50000");
            assertThat(first.getChannel()).isEqualTo(PromotionChannel.POS);
            assertThat(second.getReservationToken()).isNotEqualTo(first.getReservationToken());
            assertThat(coupon.getUsedCount()).isEqualTo(2);

            assertThatThrownBy(() -> reserve(UUID.randomUUID()))
                    .isInstanceOf(BusinessException.class).hasMessage("Coupon usage limit has been reached");
            assertThat(coupon.getUsedCount()).isEqualTo(2);
            assertThat(storedUsages).hasSize(2);
        }

        @ParameterizedTest(name = "a reservation that is {0} gives its quota back")
        @EnumSource(value = CouponUsageStatus.class, names = {"RELEASED", "CANCELLED", "EXPIRED"})
        void closingReservation_freesQuota(CouponUsageStatus closedStatus) {
            CouponUsageResponse first = reserve(UUID.randomUUID());
            reserve(UUID.randomUUID());

            CouponUsageResponse closed = switch (closedStatus) {
                case RELEASED -> couponUsageService.releaseUsage(first.getId());
                case CANCELLED -> couponUsageService.cancelUsage(first.getId());
                default -> couponUsageService.expireUsage(first.getId());
            };

            assertThat(closed.getStatus()).isEqualTo(closedStatus);
            assertThat(coupon.getUsedCount()).isEqualTo(1);
            assertThat(reserve(UUID.randomUUID()).getStatus()).isEqualTo(CouponUsageStatus.RESERVED);
            assertThat(coupon.getUsedCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("an applied usage keeps its quota for good")
        void appliedUsage_keepsQuota() {
            CouponUsageResponse first = reserve(UUID.randomUUID());
            reserve(UUID.randomUUID());

            CouponUsageResponse applied = couponUsageService.applyUsage(first.getId());

            assertThat(applied.getStatus()).isEqualTo(CouponUsageStatus.APPLIED);
            assertThat(applied.getUsedAt()).isNotNull();
            assertThat(coupon.getUsedCount()).isEqualTo(2);
            assertThatThrownBy(() -> couponUsageService.releaseUsage(first.getId()))
                    .isInstanceOf(BusinessException.class).hasMessage("Only reserved coupon usage can be released");
            assertThatThrownBy(() -> couponUsageService.cancelUsage(first.getId()))
                    .isInstanceOf(BusinessException.class).hasMessage("Applied coupon usage cannot be cancelled");
            assertThatThrownBy(() -> couponUsageService.expireUsage(first.getId()))
                    .isInstanceOf(BusinessException.class).hasMessage("Only reserved coupon usage can be expired");
            assertThat(coupon.getUsedCount()).isEqualTo(2);
            assertThatThrownBy(() -> reserve(UUID.randomUUID()))
                    .isInstanceOf(BusinessException.class).hasMessage("Coupon usage limit has been reached");
        }

        @Test
        @DisplayName("releasing twice gives the quota back only once")
        void releaseTwice_rejected() {
            CouponUsageResponse first = reserve(UUID.randomUUID());
            reserve(UUID.randomUUID());
            couponUsageService.releaseUsage(first.getId());

            assertThatThrownBy(() -> couponUsageService.releaseUsage(first.getId()))
                    .isInstanceOf(BusinessException.class).hasMessage("Only reserved coupon usage can be released");
            assertThat(coupon.getUsedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("cancelling an already released usage does not give the quota back a second time")
        void cancelAfterRelease_keepsQuota() {
            CouponUsageResponse first = reserve(UUID.randomUUID());
            reserve(UUID.randomUUID());
            couponUsageService.releaseUsage(first.getId());

            CouponUsageResponse cancelled = couponUsageService.cancelUsage(first.getId());

            assertThat(cancelled.getStatus()).isEqualTo(CouponUsageStatus.CANCELLED);
            assertThat(coupon.getUsedCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("per-customer limit")
    class PerCustomerLimit {

        final UUID customerId = UUID.randomUUID();

        @BeforeEach
        void roomForManyCustomers() {
            coupon.setUsageLimit(10);
        }

        @Test
        @DisplayName("a customer cannot hold more reservations than perCustomerLimit")
        void secondOrderOfSameCustomer_rejected() {
            reserve(customerId);

            assertThatThrownBy(() -> reserve(customerId))
                    .isInstanceOf(BusinessException.class).hasMessage("Customer has reached coupon usage limit");
            assertThat(coupon.getUsedCount()).isEqualTo(1);
            assertThat(reserve(UUID.randomUUID()).getStatus()).isEqualTo(CouponUsageStatus.RESERVED);
        }

        @Test
        @DisplayName("an applied usage still counts against the customer")
        void appliedUsage_countsAgainstCustomer() {
            CouponUsageResponse first = reserve(customerId);
            couponUsageService.applyUsage(first.getId());

            assertThatThrownBy(() -> reserve(customerId))
                    .isInstanceOf(BusinessException.class).hasMessage("Customer has reached coupon usage limit");
        }

        @Test
        @DisplayName("a released reservation no longer counts against the customer")
        void releasedUsage_freesCustomerSlot() {
            CouponUsageResponse first = reserve(customerId);
            couponUsageService.releaseUsage(first.getId());

            CouponUsageResponse again = reserve(customerId);

            assertThat(again.getId()).isNotEqualTo(first.getId());
            assertThat(again.getStatus()).isEqualTo(CouponUsageStatus.RESERVED);
            assertThat(coupon.getUsedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("perCustomerLimit 2 allows exactly two orders of the same customer")
        void perCustomerLimitTwo() {
            coupon.setPerCustomerLimit(2);
            reserve(customerId);
            reserve(customerId);

            assertThatThrownBy(() -> reserve(customerId))
                    .isInstanceOf(BusinessException.class).hasMessage("Customer has reached coupon usage limit");
            assertThat(coupon.getUsedCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("reserving twice for the same order returns the same reservation and consumes one unit")
        void sameOrderTwice_idempotent() {
            UUID orderId = UUID.randomUUID();

            CouponUsageResponse first = reserve(customerId, orderId);
            CouponUsageResponse duplicate = reserve(customerId, orderId);

            assertThat(duplicate.getId()).isEqualTo(first.getId());
            assertThat(duplicate.getReservationToken()).isEqualTo(first.getReservationToken());
            assertThat(coupon.getUsedCount()).isEqualTo(1);
            assertThat(storedUsages).hasSize(1);
        }

        @Test
        @DisplayName("the same order can reserve again after its reservation was released")
        void sameOrderAfterRelease_newReservation() {
            UUID orderId = UUID.randomUUID();
            CouponUsageResponse first = reserve(customerId, orderId);
            couponUsageService.releaseUsage(first.getId());

            CouponUsageResponse second = reserve(customerId, orderId);

            assertThat(second.getId()).isNotEqualTo(first.getId());
            assertThat(storedUsages).extracting(CouponUsage::getStatus)
                    .containsExactly(CouponUsageStatus.RELEASED, CouponUsageStatus.RESERVED);
            assertThat(coupon.getUsedCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("usage state transitions")
    class UsageTransitions {

        @ParameterizedTest(name = "a {0} reservation cannot be applied afterwards")
        @EnumSource(value = CouponUsageStatus.class, names = {"RELEASED", "CANCELLED", "EXPIRED"})
        void closedReservation_cannotBeApplied(CouponUsageStatus closedStatus) {
            CouponUsageResponse usage = reserve(UUID.randomUUID());
            switch (closedStatus) {
                case RELEASED -> couponUsageService.releaseUsage(usage.getId());
                case CANCELLED -> couponUsageService.cancelUsage(usage.getId());
                default -> couponUsageService.expireUsage(usage.getId());
            }

            assertThatThrownBy(() -> couponUsageService.applyUsage(usage.getId()))
                    .isInstanceOf(BusinessException.class).hasMessage("Only reserved coupon usage can be applied");
            assertThat(storedUsages.get(0).getStatus()).isEqualTo(closedStatus);
            assertThat(storedUsages.get(0).getUsedAt()).isNull();
        }

        @Test
        @DisplayName("applying twice keeps the first usedAt")
        void applyTwice_idempotent() {
            CouponUsageResponse usage = reserve(UUID.randomUUID());
            LocalDateTime usedAt = couponUsageService.applyUsage(usage.getId()).getUsedAt();

            CouponUsageResponse again = couponUsageService.applyUsage(usage.getId());

            assertThat(again.getStatus()).isEqualTo(CouponUsageStatus.APPLIED);
            assertThat(again.getUsedAt()).isEqualTo(usedAt);
            assertThat(coupon.getUsedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("cancelling twice is rejected")
        void cancelTwice_rejected() {
            CouponUsageResponse usage = reserve(UUID.randomUUID());
            couponUsageService.cancelUsage(usage.getId());

            assertThatThrownBy(() -> couponUsageService.cancelUsage(usage.getId()))
                    .isInstanceOf(BusinessException.class).hasMessage("Coupon usage is already cancelled");
            assertThat(coupon.getUsedCount()).isZero();
        }

        @Test
        @DisplayName("release stamps releasedAt")
        void release_setsReleasedAt() {
            CouponUsageResponse usage = reserve(UUID.randomUUID());

            CouponUsageResponse released = couponUsageService.releaseUsage(usage.getId());

            assertThat(released.getReleasedAt()).isNotNull();
            assertThat(released.getUsedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("coupons that cannot be reserved")
    class NotReservable {

        void assertRejected(String message) {
            assertThatThrownBy(() -> reserve(UUID.randomUUID()))
                    .isInstanceOf(BusinessException.class).hasMessage(message);
            assertThat(coupon.getUsedCount()).isZero();
            assertThat(storedUsages).isEmpty();
        }

        @ParameterizedTest(name = "{0} coupon cannot be reserved")
        @EnumSource(value = CouponStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
        void inactiveCoupon_rejected(CouponStatus status) {
            coupon.setStatus(status);

            assertRejected("Only active coupon can be reserved");
        }

        @ParameterizedTest(name = "coupon of a {0} promotion cannot be reserved")
        @EnumSource(value = PromotionStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
        void inactivePromotion_rejected(PromotionStatus status) {
            promotion.setStatus(status);

            assertRejected("Coupon promotion is not active");
        }

        @Test
        @DisplayName("a coupon past expiresAt cannot be reserved, one expiring later can")
        void expiredCoupon_rejected() {
            coupon.setExpiresAt(LocalDateTime.now().minusMinutes(1));

            assertRejected("Expired coupon cannot be reserved");

            coupon.setExpiresAt(LocalDateTime.now().plusMinutes(1));
            assertThat(reserve(UUID.randomUUID()).getStatus()).isEqualTo(CouponUsageStatus.RESERVED);
        }

        @Test
        @DisplayName("a personal coupon only works for its owner")
        void personalCoupon_onlyOwner() {
            UUID owner = UUID.randomUUID();
            coupon.setScope(CouponScope.PERSONAL);
            coupon.setCustomerId(owner);

            assertRejected("Personal coupon does not belong to this customer");
            assertThat(reserve(owner).getCustomerId()).isEqualTo(owner);
            assertThat(coupon.getUsedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("a coupon without counters behaves like a single-use coupon")
        void nullCounters_defaultToSingleUse() {
            coupon.setUsageLimit(null);
            coupon.setUsedCount(null);
            coupon.setPerCustomerLimit(null);

            reserve(UUID.randomUUID());

            assertThat(coupon.getUsedCount()).isEqualTo(1);
            assertThatThrownBy(() -> reserve(UUID.randomUUID()))
                    .isInstanceOf(BusinessException.class).hasMessage("Coupon usage limit has been reached");
        }
    }
}
