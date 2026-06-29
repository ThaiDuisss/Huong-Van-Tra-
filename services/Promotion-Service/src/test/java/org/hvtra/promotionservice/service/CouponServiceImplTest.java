//package org.hvtra.promotionservice.service;
//
//import org.hvtra.commonservice.exception.ResourceAlreadyExistsException;
//import org.hvtra.commonservice.exception.ResourceNotFoundException;
//import org.hvtra.promotionservice.dto.request.coupon.CouponCreateRequest;
//import org.hvtra.promotionservice.dto.request.coupon.CouponStatusUpdateRequest;
//import org.hvtra.promotionservice.dto.request.coupon.CouponUpdateRequest;
//import org.hvtra.promotionservice.dto.response.CouponResponse;
//import org.hvtra.promotionservice.dto.response.PageResponse;
//import org.hvtra.promotionservice.entity.Promotion;
//import org.hvtra.promotionservice.entity.coupon.Coupon;
//import org.hvtra.promotionservice.mapper.CouponMapper;
//import org.hvtra.promotionservice.repository.PromotionRepository;
//import org.hvtra.promotionservice.repository.coupon.CouponRepository;
//import org.hvtra.promotionservice.service.impl.CouponServiceImpl;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Nested;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.PageImpl;
//import org.springframework.data.domain.Pageable;
//
//import java.util.List;
//import java.util.Optional;
//import java.util.UUID;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//@DisplayName("CouponServiceImpl Test")
//class CouponServiceImplTest {
//
//    @Mock
//    PromotionRepository promotionRepository;
//
//    @Mock
//    CouponRepository couponRepository;
//
//    @Mock
//    CouponMapper couponMapper;
//
//    @InjectMocks
//    CouponServiceImpl couponService;
//
//    UUID promotionId;
//    UUID couponId;
//
//    Promotion promotion;
//    Coupon coupon;
//    CouponResponse couponResponse;
//
//    @BeforeEach
//    void setUp() {
//        promotionId = UUID.randomUUID();
//        couponId = UUID.randomUUID();
//
//        promotion = new Promotion();
//        promotion.setId(promotionId);
//
//        coupon = new Coupon();
//        coupon.setId(couponId);
//        coupon.setCode("SALE10");
//        coupon.setPromotion(promotion);
//        coupon.setIsDeleted(false);
//
//        couponResponse = CouponResponse.builder()
//                .id(couponId)
//                .code("SALE10")
//                .build();
//    }
//
//    @Nested
//    @DisplayName("createCoupon")
//    class CreateCouponTest {
//
//        @Test
//        @DisplayName("Should create coupon successfully")
//        void createCoupon_success() {
//            CouponCreateRequest request = mock(CouponCreateRequest.class);
//
//            Coupon couponToSave = new Coupon();
//            couponToSave.setCode("SALE10");
//
//            when(request.getCode()).thenReturn("SALE10");
//            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
//                    .thenReturn(Optional.of(promotion));
//            when(couponRepository.existsByCodeAndIsDeletedFalse("SALE10"))
//                    .thenReturn(false);
//            when(couponMapper.toEntity(request))
//                    .thenReturn(couponToSave);
//            when(couponRepository.save(couponToSave))
//                    .thenReturn(coupon);
//            when(couponMapper.toResponse(coupon))
//                    .thenReturn(couponResponse);
//
//            CouponResponse result = couponService.createCoupon(promotionId, request);
//
//            assertNotNull(result);
//            assertEquals(couponId, result.getId());
//            assertEquals("SALE10", result.getCode());
//            assertEquals(promotion, couponToSave.getPromotion());
//
//            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
//            verify(couponRepository).existsByCodeAndIsDeletedFalse("SALE10");
//            verify(couponMapper).toEntity(request);
//            verify(couponRepository).save(couponToSave);
//            verify(couponMapper).toResponse(coupon);
//        }
//
//        @Test
//        @DisplayName("Should throw ResourceNotFoundException when promotion not found")
//        void createCoupon_promotionNotFound_throwException() {
//            CouponCreateRequest request = mock(CouponCreateRequest.class);
//
//            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
//                    .thenReturn(Optional.empty());
//
//            assertThrows(
//                    ResourceNotFoundException.class,
//                    () -> couponService.createCoupon(promotionId, request)
//            );
//
//            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
//            verifyNoInteractions(couponRepository);
//            verifyNoInteractions(couponMapper);
//        }
//
//        @Test
//        @DisplayName("Should throw ResourceAlreadyExistsException when coupon code already exists")
//        void createCoupon_codeAlreadyExists_throwException() {
//            CouponCreateRequest request = mock(CouponCreateRequest.class);
//
//            when(request.getCode()).thenReturn("SALE10");
//            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
//                    .thenReturn(Optional.of(promotion));
//            when(couponRepository.existsByCodeAndIsDeletedFalse("SALE10"))
//                    .thenReturn(true);
//
//            assertThrows(
//                    ResourceAlreadyExistsException.class,
//                    () -> couponService.createCoupon(promotionId, request)
//            );
//
//            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
//            verify(couponRepository).existsByCodeAndIsDeletedFalse("SALE10");
//            verifyNoInteractions(couponMapper);
//            verify(couponRepository, never()).save(any(Coupon.class));
//        }
//    }
//
//    @Nested
//    @DisplayName("getCouponById")
//    class GetCouponByIdTest {
//
//        @Test
//        @DisplayName("Should get coupon by id successfully")
//        void getCouponById_success() {
//            when(couponRepository.findByIdAndIsDeletedFalse(couponId))
//                    .thenReturn(Optional.of(coupon));
//            when(couponMapper.toResponse(coupon))
//                    .thenReturn(couponResponse);
//
//            CouponResponse result = couponService.getCouponById(couponId);
//
//            assertNotNull(result);
//            assertEquals(couponId, result.getId());
//            assertEquals("SALE10", result.getCode());
//
//            verify(couponRepository).findByIdAndIsDeletedFalse(couponId);
//            verify(couponMapper).toResponse(coupon);
//        }
//
//        @Test
//        @DisplayName("Should throw ResourceNotFoundException when coupon not found")
//        void getCouponById_notFound_throwException() {
//            when(couponRepository.findByIdAndIsDeletedFalse(couponId))
//                    .thenReturn(Optional.empty());
//
//            assertThrows(
//                    ResourceNotFoundException.class,
//                    () -> couponService.getCouponById(couponId)
//            );
//
//            verify(couponRepository).findByIdAndIsDeletedFalse(couponId);
//            verifyNoInteractions(couponMapper);
//        }
//    }
//
//    @Nested
//    @DisplayName("getCouponsByPromotion")
//    class GetCouponsByPromotionTest {
//
//        @Test
//        @DisplayName("Should get coupons by promotion successfully")
//        void getCouponsByPromotion_success() {
//            Page<Coupon> couponPage = new PageImpl<>(List.of(coupon));
//
//            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
//                    .thenReturn(Optional.of(promotion));
//            when(couponRepository.findAllByPromotionIdAndIsDeletedFalse(
//                    eq(promotionId),
//                    any(Pageable.class)
//            )).thenReturn(couponPage);
//            when(couponMapper.toResponse(coupon))
//                    .thenReturn(couponResponse);
//
//            PageResponse<CouponResponse> result =
//                    couponService.getCouponsByPromotion(promotionId, 0, 10);
//
//            assertNotNull(result);
//            assertEquals(0, result.getPage());
//            assertEquals(1, result.getTotalElements());
//            assertEquals(1, result.getContent().size());
//            assertEquals(couponId, result.getContent().getFirst().getId());
//
//            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
//            verify(couponRepository).findAllByPromotionIdAndIsDeletedFalse(
//                    eq(promotionId),
//                    any(Pageable.class)
//            );
//            verify(couponMapper).toResponse(coupon);
//        }
//
//        @Test
//        @DisplayName("Should throw ResourceNotFoundException when promotion not found")
//        void getCouponsByPromotion_promotionNotFound_throwException() {
//            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
//                    .thenReturn(Optional.empty());
//
//            assertThrows(
//                    ResourceNotFoundException.class,
//                    () -> couponService.getCouponsByPromotion(promotionId, 0, 10)
//            );
//
//            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
//            verifyNoInteractions(couponRepository);
//            verifyNoInteractions(couponMapper);
//        }
//    }
//
//    @Nested
//    @DisplayName("updateCoupon")
//    class UpdateCouponTest {
//
//        @Test
//        @DisplayName("Should update coupon successfully")
//        void updateCoupon_success() {
//            CouponUpdateRequest request = mock(CouponUpdateRequest.class);
//
//            when(request.getCode()).thenReturn("SALE20");
//            when(couponRepository.findByIdAndIsDeletedFalse(couponId))
//                    .thenReturn(Optional.of(coupon));
//            when(couponRepository.existsByCodeAndIdNotAndIsDeletedFalse("SALE20", couponId))
//                    .thenReturn(false);
//            when(couponRepository.save(coupon))
//                    .thenReturn(coupon);
//            when(couponMapper.toResponse(coupon))
//                    .thenReturn(couponResponse);
//
//            CouponResponse result = couponService.updateCoupon(couponId, request);
//
//            assertNotNull(result);
//            assertEquals(couponId, result.getId());
//
//            verify(couponRepository).findByIdAndIsDeletedFalse(couponId);
//            verify(couponRepository).existsByCodeAndIdNotAndIsDeletedFalse("SALE20", couponId);
//            verify(couponMapper).updateEntityFromRequest(request, coupon);
//            verify(couponRepository).save(coupon);
//            verify(couponMapper).toResponse(coupon);
//        }
//
//        @Test
//        @DisplayName("Should throw ResourceNotFoundException when coupon not found")
//        void updateCoupon_couponNotFound_throwException() {
//            CouponUpdateRequest request = mock(CouponUpdateRequest.class);
//
//            when(couponRepository.findByIdAndIsDeletedFalse(couponId))
//                    .thenReturn(Optional.empty());
//
//            assertThrows(
//                    ResourceNotFoundException.class,
//                    () -> couponService.updateCoupon(couponId, request)
//            );
//
//            verify(couponRepository).findByIdAndIsDeletedFalse(couponId);
//            verify(couponRepository, never())
//                    .existsByCodeAndIdNotAndIsDeletedFalse(anyString(), any(UUID.class));
//            verifyNoInteractions(couponMapper);
//            verify(couponRepository, never()).save(any(Coupon.class));
//        }
//
//        @Test
//        @DisplayName("Should throw ResourceAlreadyExistsException when coupon code used by another coupon")
//        void updateCoupon_codeAlreadyUsedByAnotherCoupon_throwException() {
//            CouponUpdateRequest request = mock(CouponUpdateRequest.class);
//
//            when(request.getCode()).thenReturn("SALE20");
//            when(couponRepository.findByIdAndIsDeletedFalse(couponId))
//                    .thenReturn(Optional.of(coupon));
//            when(couponRepository.existsByCodeAndIdNotAndIsDeletedFalse("SALE20", couponId))
//                    .thenReturn(true);
//
//            assertThrows(
//                    ResourceAlreadyExistsException.class,
//                    () -> couponService.updateCoupon(couponId, request)
//            );
//
//            verify(couponRepository).findByIdAndIsDeletedFalse(couponId);
//            verify(couponRepository).existsByCodeAndIdNotAndIsDeletedFalse("SALE20", couponId);
//            verify(couponMapper, never()).updateEntityFromRequest(any(), any());
//            verify(couponRepository, never()).save(any(Coupon.class));
//            verify(couponMapper, never()).toResponse(any(Coupon.class));
//        }
//    }
//
//    @Nested
//    @DisplayName("updateCouponStatus")
//    class UpdateCouponStatusTest {
//
//        @Test
//        @DisplayName("Should update coupon status successfully")
//        void updateCouponStatus_success() {
//            CouponStatusUpdateRequest request = mock(CouponStatusUpdateRequest.class);
//
//            when(couponRepository.findByIdAndIsDeletedFalse(couponId))
//                    .thenReturn(Optional.of(coupon));
//            when(couponRepository.save(coupon))
//                    .thenReturn(coupon);
//            when(couponMapper.toResponse(coupon))
//                    .thenReturn(couponResponse);
//
//            CouponResponse result = couponService.updateCouponStatus(couponId, request);
//
//            assertNotNull(result);
//            assertEquals(couponId, result.getId());
//
//            verify(couponRepository).findByIdAndIsDeletedFalse(couponId);
//            verify(request).getStatus();
//            verify(couponRepository).save(coupon);
//            verify(couponMapper).toResponse(coupon);
//        }
//
//        @Test
//        @DisplayName("Should throw ResourceNotFoundException when coupon not found")
//        void updateCouponStatus_couponNotFound_throwException() {
//            CouponStatusUpdateRequest request = mock(CouponStatusUpdateRequest.class);
//
//            when(couponRepository.findByIdAndIsDeletedFalse(couponId))
//                    .thenReturn(Optional.empty());
//
//            assertThrows(
//                    ResourceNotFoundException.class,
//                    () -> couponService.updateCouponStatus(couponId, request)
//            );
//
//            verify(couponRepository).findByIdAndIsDeletedFalse(couponId);
//            verify(request, never()).getStatus();
//            verify(couponRepository, never()).save(any(Coupon.class));
//            verifyNoInteractions(couponMapper);
//        }
//    }
//
//    @Nested
//    @DisplayName("deleteCoupon")
//    class DeleteCouponTest {
//
//        @Test
//        @DisplayName("Should delete coupon successfully")
//        void deleteCoupon_success() {
//            when(couponRepository.findByIdAndIsDeletedFalse(couponId))
//                    .thenReturn(Optional.of(coupon));
//            when(couponRepository.save(coupon))
//                    .thenReturn(coupon);
//
//            couponService.deleteCoupon(couponId);
//
//            assertTrue(coupon.getIsDeleted());
//
//            verify(couponRepository).findByIdAndIsDeletedFalse(couponId);
//            verify(couponRepository).save(coupon);
//        }
//
//        @Test
//        @DisplayName("Should throw ResourceNotFoundException when coupon not found")
//        void deleteCoupon_couponNotFound_throwException() {
//            when(couponRepository.findByIdAndIsDeletedFalse(couponId))
//                    .thenReturn(Optional.empty());
//
//            assertThrows(
//                    ResourceNotFoundException.class,
//                    () -> couponService.deleteCoupon(couponId)
//            );
//
//            verify(couponRepository).findByIdAndIsDeletedFalse(couponId);
//            verify(couponRepository, never()).save(any(Coupon.class));
//        }
//    }
//}
