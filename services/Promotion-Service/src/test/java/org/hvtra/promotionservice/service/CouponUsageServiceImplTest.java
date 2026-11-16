package org.hvtra.promotionservice.service;

import org.hvtra.commonservice.common_core.exception.BusinessException;
import org.hvtra.commonservice.common_core.exception.ResourceNotFoundException;
import org.hvtra.promotionservice.dto.request.couponusage.CouponUsageFilterRequest;
import org.hvtra.promotionservice.dto.request.couponusage.CouponUsageReserveRequest;
import org.hvtra.promotionservice.dto.response.CouponUsageResponse;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.entity.Promotion;
import org.hvtra.promotionservice.entity.coupon.Coupon;
import org.hvtra.promotionservice.entity.coupon.CouponUsage;
import org.hvtra.promotionservice.enums.coupon.CouponStatus;
import org.hvtra.promotionservice.enums.coupon.CouponUsageStatus;
import org.hvtra.promotionservice.enums.promotion.PromotionChannel;
import org.hvtra.promotionservice.enums.promotion.PromotionStatus;
import org.hvtra.promotionservice.mapper.CouponUsageMapper;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponUsageServiceImpl")
class CouponUsageServiceImplTest {

    @Mock
    CouponUsageRepository couponUsageRepository;

    @Mock
    CouponRepository couponRepository;

    @Mock
    CouponUsageMapper couponUsageMapper;

    @Mock
    CouponUsageValidator couponUsageValidator;

    @Mock
    CouponValidator couponValidator;

    @InjectMocks
    CouponUsageServiceImpl couponUsageService;

    UUID couponId;
    UUID usageId;
    UUID orderId;
    UUID customerId;
    Coupon coupon;
    CouponUsage usage;
    CouponUsageResponse response;

    @BeforeEach
    void setUp() {
        couponId = UUID.randomUUID();
        usageId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        Promotion promotion = Promotion.builder().status(PromotionStatus.ACTIVE).build();
        coupon = Coupon.builder().code("TET2027").status(CouponStatus.ACTIVE).promotion(promotion)
                .usageLimit(10).usedCount(3).perCustomerLimit(2).build();
        coupon.setId(couponId);
        usage = CouponUsage.builder().coupon(coupon).orderId(orderId).customerId(customerId)
                .status(CouponUsageStatus.RESERVED).build();
        usage.setId(usageId);
        response = CouponUsageResponse.builder().id(usageId).build();
    }

    @Nested
    @DisplayName("list and read")
    class ListAndRead {

        @Test
        @DisplayName("getCouponUsages trims the coupon code and forwards every filter")
        void getCouponUsages_forwardsFilters() {
            LocalDateTime from = LocalDateTime.of(2027, 1, 1, 0, 0);
            LocalDateTime to = LocalDateTime.of(2027, 1, 31, 0, 0);
            CouponUsageFilterRequest filter = CouponUsageFilterRequest.builder().couponId(couponId)
                    .couponCode(" tet ").orderId(orderId).customerId(customerId).status(CouponUsageStatus.APPLIED)
                    .reservedFrom(from).reservedTo(to).build();
            when(couponUsageRepository.searchCouponUsages(eq(couponId), eq("tet"), eq(orderId), eq(customerId),
                    eq(CouponUsageStatus.APPLIED), eq(from), eq(to), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(usage)));
            when(couponUsageMapper.toResponse(usage)).thenReturn(response);

            PageResponse<CouponUsageResponse> result = couponUsageService.getCouponUsages(filter, 0, 10);

            assertThat(result.getContent()).containsExactly(response);
        }

        @Test
        @DisplayName("getCouponUsages accepts a null filter")
        void getCouponUsages_nullFilter() {
            when(couponUsageRepository.searchCouponUsages(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                    isNull(), any(Pageable.class))).thenReturn(Page.empty());

            assertThat(couponUsageService.getCouponUsages(null, 0, 10).getContent()).isEmpty();
            assertThat(couponUsageService.getAllCouponUsages(0, 10).getContent()).isEmpty();
        }

        @Test
        @DisplayName("getCouponUsages rejects reservedFrom after reservedTo")
        void getCouponUsages_invalidRange_throws() {
            CouponUsageFilterRequest filter = CouponUsageFilterRequest.builder()
                    .reservedFrom(LocalDateTime.of(2027, 2, 1, 0, 0))
                    .reservedTo(LocalDateTime.of(2027, 1, 1, 0, 0)).build();

            assertThatThrownBy(() -> couponUsageService.getCouponUsages(filter, 0, 10))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Reserved from");
            verifyNoInteractions(couponUsageRepository);
        }

        @Test
        @DisplayName("getUsagesByCoupon checks the coupon and filters by its id")
        void getUsagesByCoupon_filtersByCoupon() {
            when(couponRepository.findByIdAndIsDeletedFalse(couponId)).thenReturn(Optional.of(coupon));
            when(couponUsageRepository.searchCouponUsages(eq(couponId), isNull(), isNull(), isNull(), isNull(),
                    isNull(), isNull(), any(Pageable.class))).thenReturn(Page.empty());

            assertThat(couponUsageService.getUsagesByCoupon(couponId, 0, 10).getContent()).isEmpty();
        }

        @Test
        @DisplayName("getUsagesByCoupon throws when the coupon is missing")
        void getUsagesByCoupon_missingCoupon_throws() {
            when(couponRepository.findByIdAndIsDeletedFalse(couponId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> couponUsageService.getUsagesByCoupon(couponId, 0, 10))
                    .isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(couponUsageRepository);
        }

        @Test
        @DisplayName("getUsagesByOrder and getUsagesByCustomer filter by the given id")
        void getUsagesByOrderAndCustomer_filter() {
            when(couponUsageRepository.searchCouponUsages(isNull(), isNull(), eq(orderId), isNull(), isNull(),
                    isNull(), isNull(), any(Pageable.class))).thenReturn(Page.empty());
            when(couponUsageRepository.searchCouponUsages(isNull(), isNull(), isNull(), eq(customerId), isNull(),
                    isNull(), isNull(), any(Pageable.class))).thenReturn(Page.empty());

            assertThat(couponUsageService.getUsagesByOrder(orderId, 0, 10).getContent()).isEmpty();
            assertThat(couponUsageService.getUsagesByCustomer(customerId, 0, 10).getContent()).isEmpty();
        }

        @Test
        @DisplayName("getCouponUsageById returns the mapped usage or throws when missing")
        void getCouponUsageById() {
            when(couponUsageRepository.findByIdAndIsDeletedFalse(usageId)).thenReturn(Optional.of(usage), Optional.empty());
            when(couponUsageMapper.toResponse(usage)).thenReturn(response);

            assertThat(couponUsageService.getCouponUsageById(usageId)).isSameAs(response);
            assertThatThrownBy(() -> couponUsageService.getCouponUsageById(usageId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("reserveCoupon")
    class ReserveCoupon {

        CouponUsageReserveRequest request() {
            return CouponUsageReserveRequest.builder().couponCode(" TET2027 ").orderId(orderId)
                    .customerId(customerId).discountAmount(BigDecimal.valueOf(50000))
                    .channel(PromotionChannel.POS).build();
        }

        @Test
        @DisplayName("creates a RESERVED usage, increments usedCount and sets a 15 minute reservation")
        void reserveCoupon_createsReservation() {
            CouponUsageReserveRequest request = request();
            CouponUsage mapped = CouponUsage.builder().orderId(orderId).customerId(customerId)
                    .discountAmount(BigDecimal.valueOf(50000)).build();
            when(couponRepository.findByCodeAndIsDeletedFalseForUpdate("TET2027")).thenReturn(Optional.of(coupon));
            when(couponUsageRepository.findByCouponIdAndOrderIdAndStatusAndIsDeletedFalse(couponId, orderId,
                    CouponUsageStatus.RESERVED)).thenReturn(Optional.empty());
            when(couponUsageRepository.countByCouponIdAndCustomerIdAndStatusInAndIsDeletedFalse(couponId, customerId,
                    List.of(CouponUsageStatus.RESERVED, CouponUsageStatus.APPLIED))).thenReturn(1L);
            when(couponUsageMapper.toEntity(request)).thenReturn(mapped);
            when(couponUsageRepository.save(mapped)).thenReturn(mapped);
            when(couponUsageMapper.toResponse(mapped)).thenReturn(response);

            CouponUsageResponse result = couponUsageService.reserveCoupon(request);

            assertThat(result).isSameAs(response);
            assertThat(coupon.getUsedCount()).isEqualTo(4);
            assertThat(mapped.getCoupon()).isSameAs(coupon);
            assertThat(mapped.getStatus()).isEqualTo(CouponUsageStatus.RESERVED);
            assertThat(mapped.getReservationToken()).startsWith("RES-").hasSize(36);
            assertThat(mapped.getReservedAt()).isCloseTo(LocalDateTime.now(), within(5, java.time.temporal.ChronoUnit.SECONDS));
            assertThat(mapped.getReservationExpiresAt()).isEqualTo(mapped.getReservedAt().plusMinutes(15));
            verify(couponValidator).validateBeforeReserve(coupon, request);
            verify(couponRepository).save(coupon);
        }

        @Test
        @DisplayName("returns the existing RESERVED usage of the same order without reserving again")
        void reserveCoupon_idempotentForSameOrder() {
            CouponUsageReserveRequest request = request();
            when(couponRepository.findByCodeAndIsDeletedFalseForUpdate("TET2027")).thenReturn(Optional.of(coupon));
            when(couponUsageRepository.findByCouponIdAndOrderIdAndStatusAndIsDeletedFalse(couponId, orderId,
                    CouponUsageStatus.RESERVED)).thenReturn(Optional.of(usage));
            when(couponUsageMapper.toResponse(usage)).thenReturn(response);

            CouponUsageResponse result = couponUsageService.reserveCoupon(request);

            assertThat(result).isSameAs(response);
            assertThat(coupon.getUsedCount()).isEqualTo(3);
            verifyNoInteractions(couponValidator);
            verify(couponUsageRepository, never()).save(any());
            verify(couponRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for an unknown coupon code")
        void reserveCoupon_unknownCode_throws() {
            when(couponRepository.findByCodeAndIsDeletedFalseForUpdate("TET2027")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> couponUsageService.reserveCoupon(request()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("TET2027");
            verifyNoInteractions(couponUsageRepository, couponValidator);
        }

        @Test
        @DisplayName("rejects the reservation when the customer reached the per-customer limit")
        void reserveCoupon_perCustomerLimitReached_throws() {
            CouponUsageReserveRequest request = request();
            when(couponRepository.findByCodeAndIsDeletedFalseForUpdate("TET2027")).thenReturn(Optional.of(coupon));
            when(couponUsageRepository.findByCouponIdAndOrderIdAndStatusAndIsDeletedFalse(couponId, orderId,
                    CouponUsageStatus.RESERVED)).thenReturn(Optional.empty());
            when(couponUsageRepository.countByCouponIdAndCustomerIdAndStatusInAndIsDeletedFalse(couponId, customerId,
                    List.of(CouponUsageStatus.RESERVED, CouponUsageStatus.APPLIED))).thenReturn(2L);

            assertThatThrownBy(() -> couponUsageService.reserveCoupon(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("usage limit");
            assertThat(coupon.getUsedCount()).isEqualTo(3);
            verify(couponUsageRepository, never()).save(any());
        }

        @Test
        @DisplayName("propagates the coupon validator error before counting customer usages")
        void reserveCoupon_validatorRejects_throws() {
            CouponUsageReserveRequest request = request();
            when(couponRepository.findByCodeAndIsDeletedFalseForUpdate("TET2027")).thenReturn(Optional.of(coupon));
            when(couponUsageRepository.findByCouponIdAndOrderIdAndStatusAndIsDeletedFalse(couponId, orderId,
                    CouponUsageStatus.RESERVED)).thenReturn(Optional.empty());
            doThrow(new BusinessException("Only active coupon can be reserved"))
                    .when(couponValidator).validateBeforeReserve(coupon, request);

            assertThatThrownBy(() -> couponUsageService.reserveCoupon(request)).isInstanceOf(BusinessException.class);
            verify(couponUsageRepository, never())
                    .countByCouponIdAndCustomerIdAndStatusInAndIsDeletedFalse(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("usage lifecycle")
    class UsageLifecycle {

        @Test
        @DisplayName("applyUsage marks a RESERVED usage APPLIED with usedAt")
        void applyUsage_marksApplied() {
            when(couponUsageRepository.findByIdAndIsDeletedFalse(usageId)).thenReturn(Optional.of(usage));
            when(couponUsageRepository.save(usage)).thenReturn(usage);
            when(couponUsageMapper.toResponse(usage)).thenReturn(response);

            couponUsageService.applyUsage(usageId);

            assertThat(usage.getStatus()).isEqualTo(CouponUsageStatus.APPLIED);
            assertThat(usage.getUsedAt()).isNotNull();
            assertThat(coupon.getUsedCount()).isEqualTo(3);
            verify(couponUsageValidator).validateBeforeApply(usage);
        }

        @Test
        @DisplayName("applyUsage is idempotent for an APPLIED usage")
        void applyUsage_alreadyApplied_returnsWithoutSaving() {
            usage.setStatus(CouponUsageStatus.APPLIED);
            when(couponUsageRepository.findByIdAndIsDeletedFalse(usageId)).thenReturn(Optional.of(usage));
            when(couponUsageMapper.toResponse(usage)).thenReturn(response);

            assertThat(couponUsageService.applyUsage(usageId)).isSameAs(response);

            verify(couponUsageRepository, never()).save(any());
            verifyNoInteractions(couponUsageValidator);
        }

        @Test
        @DisplayName("releaseUsage gives the quota back and marks RELEASED")
        void releaseUsage_restoresQuota() {
            when(couponUsageRepository.findByIdAndIsDeletedFalse(usageId)).thenReturn(Optional.of(usage));
            when(couponUsageRepository.save(usage)).thenReturn(usage);
            when(couponUsageMapper.toResponse(usage)).thenReturn(response);

            couponUsageService.releaseUsage(usageId);

            assertThat(usage.getStatus()).isEqualTo(CouponUsageStatus.RELEASED);
            assertThat(usage.getReleasedAt()).isNotNull();
            assertThat(coupon.getUsedCount()).isEqualTo(2);
            verify(couponUsageValidator).validateBeforeRelease(usage);
            verify(couponRepository).save(coupon);
        }

        @Test
        @DisplayName("cancelUsage restores the quota only for RESERVED usages")
        void cancelUsage_reserved_restoresQuota() {
            when(couponUsageRepository.findByIdAndIsDeletedFalse(usageId)).thenReturn(Optional.of(usage));
            when(couponUsageRepository.save(usage)).thenReturn(usage);
            when(couponUsageMapper.toResponse(usage)).thenReturn(response);

            couponUsageService.cancelUsage(usageId);

            assertThat(usage.getStatus()).isEqualTo(CouponUsageStatus.CANCELLED);
            assertThat(coupon.getUsedCount()).isEqualTo(2);
            verify(couponUsageValidator).validateBeforeCancel(usage);
        }

        @Test
        @DisplayName("cancelUsage of a RELEASED usage does not touch the coupon quota")
        void cancelUsage_released_keepsQuota() {
            usage.setStatus(CouponUsageStatus.RELEASED);
            when(couponUsageRepository.findByIdAndIsDeletedFalse(usageId)).thenReturn(Optional.of(usage));
            when(couponUsageRepository.save(usage)).thenReturn(usage);
            when(couponUsageMapper.toResponse(usage)).thenReturn(response);

            couponUsageService.cancelUsage(usageId);

            assertThat(usage.getStatus()).isEqualTo(CouponUsageStatus.CANCELLED);
            assertThat(coupon.getUsedCount()).isEqualTo(3);
            verify(couponRepository, never()).save(any());
        }

        @Test
        @DisplayName("expireUsage marks EXPIRED and never drives usedCount below zero")
        void expireUsage_restoresQuotaNotBelowZero() {
            coupon.setUsedCount(0);
            when(couponUsageRepository.findByIdAndIsDeletedFalse(usageId)).thenReturn(Optional.of(usage));
            when(couponUsageRepository.save(usage)).thenReturn(usage);
            when(couponUsageMapper.toResponse(usage)).thenReturn(response);

            couponUsageService.expireUsage(usageId);

            assertThat(usage.getStatus()).isEqualTo(CouponUsageStatus.EXPIRED);
            assertThat(coupon.getUsedCount()).isZero();
            verify(couponUsageValidator).validateBeforeExpire(usage);
        }

        @Test
        @DisplayName("lifecycle actions propagate validator errors without saving")
        void lifecycle_validatorRejects_noSave() {
            when(couponUsageRepository.findByIdAndIsDeletedFalse(usageId)).thenReturn(Optional.of(usage));
            doThrow(new BusinessException("Only reserved coupon usage can be released"))
                    .when(couponUsageValidator).validateBeforeRelease(usage);

            assertThatThrownBy(() -> couponUsageService.releaseUsage(usageId)).isInstanceOf(BusinessException.class);

            assertThat(usage.getStatus()).isEqualTo(CouponUsageStatus.RESERVED);
            assertThat(coupon.getUsedCount()).isEqualTo(3);
            verify(couponUsageRepository, never()).save(any());
        }

        @Test
        @DisplayName("lifecycle actions throw ResourceNotFoundException for a missing usage")
        void lifecycle_missingUsage_throws() {
            when(couponUsageRepository.findByIdAndIsDeletedFalse(usageId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> couponUsageService.applyUsage(usageId)).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> couponUsageService.releaseUsage(usageId)).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> couponUsageService.cancelUsage(usageId)).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> couponUsageService.expireUsage(usageId)).isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(couponUsageValidator);
        }

        @Test
        @DisplayName("releaseUsage saves the coupon with the decremented quota before the usage")
        void releaseUsage_savesCouponThenUsage() {
            when(couponUsageRepository.findByIdAndIsDeletedFalse(usageId)).thenReturn(Optional.of(usage));
            when(couponUsageRepository.save(usage)).thenReturn(usage);
            when(couponUsageMapper.toResponse(usage)).thenReturn(response);

            couponUsageService.releaseUsage(usageId);

            ArgumentCaptor<Coupon> saved = ArgumentCaptor.forClass(Coupon.class);
            verify(couponRepository).save(saved.capture());
            assertThat(saved.getValue().getUsedCount()).isEqualTo(2);
            org.mockito.InOrder order = org.mockito.Mockito.inOrder(couponRepository, couponUsageRepository);
            order.verify(couponRepository).save(coupon);
            order.verify(couponUsageRepository).save(usage);
        }
    }
}
