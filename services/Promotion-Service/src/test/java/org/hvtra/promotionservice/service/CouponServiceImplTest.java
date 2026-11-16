package org.hvtra.promotionservice.service;

import org.hvtra.commonservice.common_core.exception.BusinessException;
import org.hvtra.commonservice.common_core.exception.ResourceNotFoundException;
import org.hvtra.promotionservice.dto.request.coupon.CouponBulkGenerateRequest;
import org.hvtra.promotionservice.dto.request.coupon.CouponCreateRequest;
import org.hvtra.promotionservice.dto.request.coupon.CouponFilterRequest;
import org.hvtra.promotionservice.dto.request.coupon.CouponGeneratePersonalRequest;
import org.hvtra.promotionservice.dto.request.coupon.CouponGeneratePublicRequest;
import org.hvtra.promotionservice.dto.request.coupon.CouponStatusUpdateRequest;
import org.hvtra.promotionservice.dto.request.coupon.CouponUpdateRequest;
import org.hvtra.promotionservice.dto.response.CouponResponse;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.entity.Promotion;
import org.hvtra.promotionservice.entity.PromotionBenefit;
import org.hvtra.promotionservice.entity.coupon.Coupon;
import org.hvtra.promotionservice.enums.benefit.BenefitType;
import org.hvtra.promotionservice.enums.coupon.CouponIssueReason;
import org.hvtra.promotionservice.enums.coupon.CouponScope;
import org.hvtra.promotionservice.enums.coupon.CouponStatus;
import org.hvtra.promotionservice.enums.promotion.PromotionStatus;
import org.hvtra.promotionservice.mapper.CouponMapper;
import org.hvtra.promotionservice.repository.coupon.CouponRepository;
import org.hvtra.promotionservice.repository.promotion.PromotionRepository;
import org.hvtra.promotionservice.service.impl.CouponServiceImpl;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponServiceImpl")
class CouponServiceImplTest {

    @Mock
    CouponRepository couponRepository;

    @Mock
    PromotionRepository promotionRepository;

    @Mock
    CouponMapper couponMapper;

    @Mock
    CouponValidator couponValidator;

    @InjectMocks
    CouponServiceImpl couponService;

    UUID promotionId;
    UUID couponId;
    Promotion promotion;
    Coupon coupon;
    CouponResponse response;

    @BeforeEach
    void setUp() {
        promotionId = UUID.randomUUID();
        couponId = UUID.randomUUID();
        promotion = Promotion.builder().name("Tet 2027").status(PromotionStatus.ACTIVE).build();
        promotion.setId(promotionId);
        coupon = Coupon.builder().code("TET2027").status(CouponStatus.DRAFT).promotion(promotion).build();
        coupon.setId(couponId);
        response = CouponResponse.builder().id(couponId).code("TET2027").build();
    }

    @Nested
    @DisplayName("createCoupon")
    class CreateCoupon {

        @Test
        @DisplayName("validates the request, attaches the promotion, normalizes limits and snapshots the first benefit")
        void createCoupon_setsPromotionLimitsAndSnapshot() {
            PromotionBenefit deleted = PromotionBenefit.builder().benefitType(BenefitType.FIXED_DISCOUNT)
                    .value(BigDecimal.valueOf(5000)).build();
            deleted.setIsDeleted(true);
            PromotionBenefit percent = PromotionBenefit.builder().benefitType(BenefitType.PERCENT_DISCOUNT)
                    .value(BigDecimal.valueOf(20)).maxDiscount(BigDecimal.valueOf(100000)).build();
            promotion.getBenefits().add(deleted);
            promotion.getBenefits().add(percent);

            CouponCreateRequest request = CouponCreateRequest.builder()
                    .promotionId(promotionId).scope(CouponScope.PUBLIC).code("TET2027").build();
            Coupon entity = Coupon.builder().code("TET2027").scope(CouponScope.PUBLIC)
                    .usageLimit(null).usedCount(null).perCustomerLimit(null).build();

            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId)).thenReturn(Optional.of(promotion));
            when(couponMapper.toEntity(request)).thenReturn(entity);
            when(couponRepository.save(entity)).thenReturn(entity);
            when(couponMapper.toResponse(entity)).thenReturn(response);

            CouponResponse result = couponService.createCoupon(request);

            assertThat(result).isSameAs(response);
            assertThat(entity.getPromotion()).isSameAs(promotion);
            assertThat(entity.getUsageLimit()).isEqualTo(1);
            assertThat(entity.getUsedCount()).isZero();
            assertThat(entity.getPerCustomerLimit()).isEqualTo(1);
            assertThat(entity.getSnapshotPromotionName()).isEqualTo("Tet 2027");
            assertThat(entity.getSnapshotBenefitType()).isEqualTo(BenefitType.PERCENT_DISCOUNT);
            assertThat(entity.getSnapshotDiscountValue()).isEqualByComparingTo("20");
            assertThat(entity.getSnapshotMaxDiscount()).isEqualByComparingTo("100000");
            verify(couponValidator).validateCreateRequest(request);
            verify(couponValidator).validateBeforeCreate(promotion, request);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the promotion does not exist")
        void createCoupon_promotionMissing_throws() {
            CouponCreateRequest request = CouponCreateRequest.builder().promotionId(promotionId).build();
            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> couponService.createCoupon(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(promotionId.toString());

            verifyNoInteractions(couponMapper);
            verify(couponRepository, never()).save(any());
        }

        @Test
        @DisplayName("propagates the validator error before touching the repository")
        void createCoupon_validatorRejects_throws() {
            CouponCreateRequest request = CouponCreateRequest.builder().build();
            org.mockito.Mockito.doThrow(new BusinessException("Promotion ID is required"))
                    .when(couponValidator).validateCreateRequest(request);

            assertThatThrownBy(() -> couponService.createCoupon(request))
                    .isInstanceOf(BusinessException.class);

            verifyNoInteractions(promotionRepository, couponRepository, couponMapper);
        }

        @Test
        @DisplayName("createCouponByPromotion uses the path promotion id and skips the body validation")
        void createCouponByPromotion_usesPathId() {
            CouponCreateRequest request = CouponCreateRequest.builder().scope(CouponScope.PUBLIC).code("X").build();
            Coupon entity = Coupon.builder().code("X").build();
            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId)).thenReturn(Optional.of(promotion));
            when(couponMapper.toEntity(request)).thenReturn(entity);
            when(couponRepository.save(entity)).thenReturn(entity);
            when(couponMapper.toResponse(entity)).thenReturn(response);

            CouponResponse result = couponService.createCouponByPromotion(promotionId, request);

            assertThat(result).isSameAs(response);
            assertThat(entity.getPromotion()).isSameAs(promotion);
            assertThat(entity.getSnapshotBenefitType()).isNull();
            verify(couponValidator, never()).validateCreateRequest(any());
            verify(couponValidator).validateBeforeCreate(promotion, request);
        }
    }

    @Nested
    @DisplayName("read and list")
    class ReadAndList {

        @Test
        @DisplayName("getCouponById returns the mapped coupon")
        void getCouponById_returnsResponse() {
            when(couponRepository.findByIdAndIsDeletedFalse(couponId)).thenReturn(Optional.of(coupon));
            when(couponMapper.toResponse(coupon)).thenReturn(response);

            assertThat(couponService.getCouponById(couponId)).isSameAs(response);
        }

        @Test
        @DisplayName("getCouponById throws ResourceNotFoundException for a missing or deleted coupon")
        void getCouponById_missing_throws() {
            when(couponRepository.findByIdAndIsDeletedFalse(couponId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> couponService.getCouponById(couponId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("getCoupons trims the keyword, passes the filters and sorts by createdAt desc")
        void getCoupons_passesNormalizedFilter() {
            UUID customerId = UUID.randomUUID();
            LocalDateTime from = LocalDateTime.of(2027, 1, 1, 0, 0);
            LocalDateTime to = LocalDateTime.of(2027, 2, 1, 0, 0);
            CouponFilterRequest filter = CouponFilterRequest.builder().keyword("  tet ").promotionId(promotionId)
                    .status(CouponStatus.ACTIVE).scope(CouponScope.PERSONAL).customerId(customerId)
                    .expiresFrom(from).expiresTo(to).build();
            Page<Coupon> page = new PageImpl<>(List.of(coupon), PageRequest.of(1, 5), 6);
            when(couponRepository.searchCoupons(eq("tet"), eq(promotionId), eq(CouponStatus.ACTIVE),
                    eq(CouponScope.PERSONAL), eq(customerId), eq(from), eq(to), any(Pageable.class)))
                    .thenReturn(page);
            when(couponMapper.toResponse(coupon)).thenReturn(response);

            PageResponse<CouponResponse> result = couponService.getCoupons(filter, 1, 5);

            assertThat(result.getContent()).containsExactly(response);
            assertThat(result.getTotalElements()).isEqualTo(6);
            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(couponRepository).searchCoupons(any(), any(), any(), any(), any(), any(), any(), pageable.capture());
            assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
            assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
            assertThat(pageable.getValue().getSort().getOrderFor("createdAt")).isNotNull();
            assertThat(pageable.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
        }

        @Test
        @DisplayName("getCoupons accepts a null filter and a blank keyword")
        void getCoupons_nullFilter_usesDefaults() {
            when(couponRepository.searchCoupons(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                    any(Pageable.class))).thenReturn(Page.empty());

            PageResponse<CouponResponse> result = couponService.getCoupons(null, 0, 10);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("getCoupons rejects expiresFrom after expiresTo")
        void getCoupons_invalidExpireRange_throws() {
            CouponFilterRequest filter = CouponFilterRequest.builder()
                    .expiresFrom(LocalDateTime.of(2027, 3, 1, 0, 0))
                    .expiresTo(LocalDateTime.of(2027, 2, 1, 0, 0)).build();

            assertThatThrownBy(() -> couponService.getCoupons(filter, 0, 10))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Expires from");

            verifyNoInteractions(couponRepository);
        }

        @Test
        @DisplayName("getAllCoupons delegates to the empty filter")
        void getAllCoupons_delegates() {
            when(couponRepository.searchCoupons(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                    any(Pageable.class))).thenReturn(Page.empty());

            assertThat(couponService.getAllCoupons(0, 20).getContent()).isEmpty();
        }

        @Test
        @DisplayName("getCouponsByPromotion checks the promotion then filters by its id")
        void getCouponsByPromotion_filtersByPromotion() {
            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId)).thenReturn(Optional.of(promotion));
            when(couponRepository.searchCoupons(isNull(), eq(promotionId), isNull(), isNull(), isNull(), isNull(),
                    isNull(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(coupon)));
            when(couponMapper.toResponse(coupon)).thenReturn(response);

            assertThat(couponService.getCouponsByPromotion(promotionId, 0, 10).getContent()).containsExactly(response);
        }

        @Test
        @DisplayName("getCouponsByPromotion throws when the promotion is missing")
        void getCouponsByPromotion_missingPromotion_throws() {
            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> couponService.getCouponsByPromotion(promotionId, 0, 10))
                    .isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(couponRepository);
        }
    }

    @Nested
    @DisplayName("update, status and delete")
    class UpdateAndDelete {

        @Test
        @DisplayName("updateCoupon validates, applies the mapper and restores default limits")
        void updateCoupon_appliesMapperAndLimits() {
            CouponUpdateRequest request = CouponUpdateRequest.builder().code("TET2027").scope(CouponScope.PUBLIC).build();
            coupon.setUsageLimit(null);
            coupon.setPerCustomerLimit(null);
            when(couponRepository.findByIdAndIsDeletedFalse(couponId)).thenReturn(Optional.of(coupon));
            when(couponRepository.save(coupon)).thenReturn(coupon);
            when(couponMapper.toResponse(coupon)).thenReturn(response);

            CouponResponse result = couponService.updateCoupon(couponId, request);

            assertThat(result).isSameAs(response);
            assertThat(coupon.getUsageLimit()).isEqualTo(1);
            assertThat(coupon.getPerCustomerLimit()).isEqualTo(1);
            verify(couponValidator).validateBeforeUpdate(coupon, request);
            verify(couponMapper).updateEntityFromRequest(request, coupon);
        }

        @Test
        @DisplayName("updateCoupon throws ResourceNotFoundException when the coupon is missing")
        void updateCoupon_missing_throws() {
            when(couponRepository.findByIdAndIsDeletedFalse(couponId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> couponService.updateCoupon(couponId, CouponUpdateRequest.builder().build()))
                    .isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(couponMapper, couponValidator);
        }

        @Test
        @DisplayName("updateCouponStatus validates the transition and stores the new status")
        void updateCouponStatus_setsStatus() {
            CouponStatusUpdateRequest request = CouponStatusUpdateRequest.builder().status(CouponStatus.PAUSED).build();
            when(couponRepository.findByIdAndIsDeletedFalse(couponId)).thenReturn(Optional.of(coupon));
            when(couponRepository.save(coupon)).thenReturn(coupon);
            when(couponMapper.toResponse(coupon)).thenReturn(response);

            couponService.updateCouponStatus(couponId, request);

            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.PAUSED);
            verify(couponValidator).validateStatusTransition(coupon, request);
        }

        @Test
        @DisplayName("deleteCoupon soft deletes the coupon after validation")
        void deleteCoupon_softDeletes() {
            when(couponRepository.findByIdAndIsDeletedFalse(couponId)).thenReturn(Optional.of(coupon));

            couponService.deleteCoupon(couponId);

            assertThat(coupon.getIsDeleted()).isTrue();
            verify(couponValidator).validateBeforeDelete(coupon);
            verify(couponRepository).save(coupon);
        }

        @Test
        @DisplayName("deleteCoupon keeps the coupon when the validator rejects it")
        void deleteCoupon_validatorRejects_keepsCoupon() {
            when(couponRepository.findByIdAndIsDeletedFalse(couponId)).thenReturn(Optional.of(coupon));
            org.mockito.Mockito.doThrow(new BusinessException("Active coupon cannot be deleted"))
                    .when(couponValidator).validateBeforeDelete(coupon);

            assertThatThrownBy(() -> couponService.deleteCoupon(couponId)).isInstanceOf(BusinessException.class);

            assertThat(coupon.getIsDeleted()).isFalse();
            verify(couponRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("lifecycle")
    class Lifecycle {

        void stubExisting() {
            when(couponRepository.findByIdAndIsDeletedFalse(couponId)).thenReturn(Optional.of(coupon));
            when(couponRepository.save(coupon)).thenReturn(coupon);
            when(couponMapper.toResponse(coupon)).thenReturn(response);
        }

        @Test
        @DisplayName("activateCoupon sets ACTIVE")
        void activateCoupon_setsActive() {
            stubExisting();
            assertThat(couponService.activateCoupon(couponId)).isSameAs(response);
            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ACTIVE);
            verify(couponValidator).validateBeforeActivate(coupon);
        }

        @Test
        @DisplayName("pauseCoupon sets PAUSED")
        void pauseCoupon_setsPaused() {
            stubExisting();
            couponService.pauseCoupon(couponId);
            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.PAUSED);
            verify(couponValidator).validateBeforePause(coupon);
        }

        @Test
        @DisplayName("cancelCoupon sets CANCELLED")
        void cancelCoupon_setsCancelled() {
            stubExisting();
            couponService.cancelCoupon(couponId);
            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.CANCELLED);
            verify(couponValidator).validateBeforeCancel(coupon);
        }

        @Test
        @DisplayName("expireCoupon sets EXPIRED")
        void expireCoupon_setsExpired() {
            stubExisting();
            couponService.expireCoupon(couponId);
            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.EXPIRED);
            verify(couponValidator).validateBeforeExpire(coupon);
        }

        @Test
        @DisplayName("archiveCoupon sets ARCHIVED")
        void archiveCoupon_setsArchived() {
            stubExisting();
            couponService.archiveCoupon(couponId);
            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ARCHIVED);
            verify(couponValidator).validateBeforeArchive(coupon);
        }

        @Test
        @DisplayName("lifecycle actions throw ResourceNotFoundException for a missing coupon")
        void lifecycle_missingCoupon_throws() {
            when(couponRepository.findByIdAndIsDeletedFalse(couponId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> couponService.activateCoupon(couponId)).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> couponService.pauseCoupon(couponId)).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> couponService.cancelCoupon(couponId)).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> couponService.expireCoupon(couponId)).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> couponService.archiveCoupon(couponId)).isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(couponValidator);
        }
    }

    @Nested
    @DisplayName("code generation")
    class CodeGeneration {

        @Test
        @DisplayName("generatePublicCoupon builds a DRAFT public coupon with an upper-cased prefix and 10 random characters")
        void generatePublicCoupon_buildsCoupon() {
            CouponGeneratePublicRequest request = CouponGeneratePublicRequest.builder().prefix(" tet27 ")
                    .usageLimit(50).perCustomerLimit(2).issueReason(CouponIssueReason.MANUAL)
                    .expiresAt(LocalDateTime.of(2027, 2, 15, 23, 59)).build();
            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId)).thenReturn(Optional.of(promotion));
            when(couponRepository.existsByCodeAndIsDeletedFalse(anyString())).thenReturn(true, false);
            when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));
            when(couponMapper.toResponse(any(Coupon.class))).thenReturn(response);

            couponService.generatePublicCoupon(promotionId, request);

            ArgumentCaptor<Coupon> saved = ArgumentCaptor.forClass(Coupon.class);
            verify(couponRepository).save(saved.capture());
            Coupon created = saved.getValue();
            assertThat(created.getCode()).matches("TET27-[0-9A-F]{10}");
            assertThat(created.getScope()).isEqualTo(CouponScope.PUBLIC);
            assertThat(created.getStatus()).isEqualTo(CouponStatus.DRAFT);
            assertThat(created.getUsageLimit()).isEqualTo(50);
            assertThat(created.getPerCustomerLimit()).isEqualTo(2);
            assertThat(created.getUsedCount()).isZero();
            assertThat(created.getIssuedAt()).isNotNull();
            assertThat(created.getSnapshotPromotionName()).isEqualTo("Tet 2027");
            verify(couponRepository, org.mockito.Mockito.times(2)).existsByCodeAndIsDeletedFalse(anyString());
            verify(couponValidator).validateGeneratePublic(request);
        }

        @Test
        @DisplayName("generatePublicCoupon falls back to prefix CP and limit 1 when nothing is given")
        void generatePublicCoupon_defaults() {
            CouponGeneratePublicRequest request = CouponGeneratePublicRequest.builder().build();
            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId)).thenReturn(Optional.of(promotion));
            when(couponRepository.existsByCodeAndIsDeletedFalse(anyString())).thenReturn(false);
            when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));
            when(couponMapper.toResponse(any(Coupon.class))).thenReturn(response);

            couponService.generatePublicCoupon(promotionId, request);

            ArgumentCaptor<Coupon> saved = ArgumentCaptor.forClass(Coupon.class);
            verify(couponRepository).save(saved.capture());
            assertThat(saved.getValue().getCode()).startsWith("CP-");
            assertThat(saved.getValue().getUsageLimit()).isEqualTo(1);
            assertThat(saved.getValue().getPerCustomerLimit()).isEqualTo(1);
        }

        @Test
        @DisplayName("generatePersonalCoupon builds a PERSONAL coupon bound to the customer and source order")
        void generatePersonalCoupon_buildsCoupon() {
            UUID customerId = UUID.randomUUID();
            UUID sourceOrderId = UUID.randomUUID();
            CouponGeneratePersonalRequest request = CouponGeneratePersonalRequest.builder().customerId(customerId)
                    .sourceOrderId(sourceOrderId).issueReason(CouponIssueReason.ORDER_AMOUNT_REACHED).build();
            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId)).thenReturn(Optional.of(promotion));
            when(couponRepository.existsByCodeAndIsDeletedFalse(anyString())).thenReturn(false);
            when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));
            when(couponMapper.toResponse(any(Coupon.class))).thenReturn(response);

            couponService.generatePersonalCoupon(promotionId, request);

            ArgumentCaptor<Coupon> saved = ArgumentCaptor.forClass(Coupon.class);
            verify(couponRepository).save(saved.capture());
            assertThat(saved.getValue().getScope()).isEqualTo(CouponScope.PERSONAL);
            assertThat(saved.getValue().getCustomerId()).isEqualTo(customerId);
            assertThat(saved.getValue().getSourceOrderId()).isEqualTo(sourceOrderId);
            assertThat(saved.getValue().getIssueReason()).isEqualTo(CouponIssueReason.ORDER_AMOUNT_REACHED);
            verify(couponValidator).validateGeneratePersonal(request);
        }

        @Test
        @DisplayName("bulkGenerateCoupons creates one PUBLIC coupon per requested quantity")
        void bulkGenerate_public_createsQuantity() {
            CouponBulkGenerateRequest request = CouponBulkGenerateRequest.builder().scope(CouponScope.PUBLIC)
                    .quantity(3).prefix("BULK").build();
            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId)).thenReturn(Optional.of(promotion));
            when(couponRepository.existsByCodeAndIsDeletedFalse(anyString())).thenReturn(false);
            when(couponRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
            when(couponMapper.toResponse(any(Coupon.class))).thenReturn(response);

            List<CouponResponse> result = couponService.bulkGenerateCoupons(promotionId, request);

            assertThat(result).hasSize(3);
            ArgumentCaptor<List<Coupon>> saved = ArgumentCaptor.forClass(List.class);
            verify(couponRepository).saveAll(saved.capture());
            assertThat(saved.getValue()).hasSize(3)
                    .allSatisfy(c -> {
                        assertThat(c.getScope()).isEqualTo(CouponScope.PUBLIC);
                        assertThat(c.getCode()).startsWith("BULK-");
                        assertThat(c.getStatus()).isEqualTo(CouponStatus.DRAFT);
                    });
            assertThat(saved.getValue().stream().map(Coupon::getCode).distinct()).hasSize(3);
            verify(couponValidator).validateBulkGenerate(request);
        }

        @Test
        @DisplayName("bulkGenerateCoupons creates one PERSONAL coupon per customer id")
        void bulkGenerate_personal_createsPerCustomer() {
            UUID c1 = UUID.randomUUID();
            UUID c2 = UUID.randomUUID();
            CouponBulkGenerateRequest request = CouponBulkGenerateRequest.builder().scope(CouponScope.PERSONAL)
                    .customerIds(List.of(c1, c2)).build();
            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId)).thenReturn(Optional.of(promotion));
            when(couponRepository.existsByCodeAndIsDeletedFalse(anyString())).thenReturn(false);
            when(couponRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
            when(couponMapper.toResponse(any(Coupon.class))).thenReturn(response);

            List<CouponResponse> result = couponService.bulkGenerateCoupons(promotionId, request);

            assertThat(result).hasSize(2);
            ArgumentCaptor<List<Coupon>> saved = ArgumentCaptor.forClass(List.class);
            verify(couponRepository).saveAll(saved.capture());
            assertThat(saved.getValue()).extracting(Coupon::getCustomerId).containsExactly(c1, c2);
            assertThat(saved.getValue()).extracting(Coupon::getScope).containsOnly(CouponScope.PERSONAL);
        }

        @Test
        @DisplayName("generation throws ResourceNotFoundException when the promotion is missing")
        void generate_missingPromotion_throws() {
            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> couponService.generatePublicCoupon(promotionId,
                    CouponGeneratePublicRequest.builder().build())).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> couponService.generatePersonalCoupon(promotionId,
                    CouponGeneratePersonalRequest.builder().build())).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> couponService.bulkGenerateCoupons(promotionId,
                    CouponBulkGenerateRequest.builder().build())).isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(couponRepository, couponValidator);
        }
    }
}
