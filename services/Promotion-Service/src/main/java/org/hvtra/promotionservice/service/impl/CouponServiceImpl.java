//package org.hvtra.promotionservice.service.impl;
//
//import lombok.AccessLevel;
//import lombok.RequiredArgsConstructor;
//import lombok.experimental.FieldDefaults;
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
//import org.hvtra.promotionservice.service.CouponService;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.data.domain.Pageable;
//import org.springframework.data.domain.Sort;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.UUID;
//
//import static org.hvtra.promotionservice.constant.coupon.CouponErrorMessage.COUPON_CODE_ALREADY_EXISTS;
//import static org.hvtra.promotionservice.constant.coupon.CouponErrorMessage.COUPON_NOT_FOUND_WITH_ID;
//import static org.hvtra.promotionservice.constant.promotion.PromotionErrorMessage.PROMOTION_NOT_FOUND_WITH_ID;
//
//@Service
//@RequiredArgsConstructor
//@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
//public class CouponServiceImpl implements CouponService {
//
//    PromotionRepository promotionRepository;
//    CouponRepository couponRepository;
//    CouponMapper couponMapper;
//
//    @Override
//    @Transactional
//    public CouponResponse createCoupon(
//            UUID promotionId,
//            CouponCreateRequest request
//    ) {
//        Promotion promotion = getPromotionOrThrow(promotionId);
//
//        validateCouponCodeNotExists(request.getCode());
//
//        Coupon coupon = couponMapper.toEntity(request);
//        coupon.setPromotion(promotion);
//
//        Coupon savedCoupon = couponRepository.save(coupon);
//
//        return couponMapper.toResponse(savedCoupon);
//    }
//
//    @Override
//    @Transactional(readOnly = true)
//    public CouponResponse getCouponById(UUID couponId) {
//        Coupon coupon = getCouponOrThrow(couponId);
//
//        return couponMapper.toResponse(coupon);
//    }
//
//    @Override
//    @Transactional(readOnly = true)
//    public PageResponse<CouponResponse> getCouponsByPromotion(
//            UUID promotionId,
//            int page,
//            int size
//    ) {
//        getPromotionOrThrow(promotionId);
//
//        Pageable pageable = PageRequest.of(
//                page,
//                size,
//                Sort.by(Sort.Direction.DESC, "createdAt")
//        );
//
//        Page<CouponResponse> couponPage = couponRepository
//                .findAllByPromotionIdAndIsDeletedFalse(promotionId, pageable)
//                .map(couponMapper::toResponse);
//
//        return PageResponse.from(couponPage);
//    }
//
//    @Override
//    @Transactional
//    public CouponResponse updateCoupon(
//            UUID couponId,
//            CouponUpdateRequest request
//    ) {
//        Coupon coupon = getCouponOrThrow(couponId);
//
//        validateCouponCodeNotUsedByAnotherCoupon(request.getCode(), couponId);
//
//        couponMapper.updateEntityFromRequest(request, coupon);
//
//        Coupon updatedCoupon = couponRepository.save(coupon);
//
//        return couponMapper.toResponse(updatedCoupon);
//    }
//
//    @Override
//    @Transactional
//    public CouponResponse updateCouponStatus(
//            UUID couponId,
//            CouponStatusUpdateRequest request
//    ) {
//        Coupon coupon = getCouponOrThrow(couponId);
//
//        coupon.setStatus(request.getStatus());
//
//        Coupon updatedCoupon = couponRepository.save(coupon);
//
//        return couponMapper.toResponse(updatedCoupon);
//    }
//
//    @Override
//    @Transactional
//    public void deleteCoupon(UUID couponId) {
//        Coupon coupon = getCouponOrThrow(couponId);
//
//        coupon.setIsDeleted(true);
//
//        couponRepository.save(coupon);
//    }
//
//    private void validateCouponCodeNotExists(String code) {
//        if (couponRepository.existsByCodeAndIsDeletedFalse(code)) {
//            throw new ResourceAlreadyExistsException(
//                    COUPON_CODE_ALREADY_EXISTS.formatted(code)
//            );
//        }
//    }
//
//    private void validateCouponCodeNotUsedByAnotherCoupon(
//            String code,
//            UUID couponId
//    ) {
//        if (couponRepository.existsByCodeAndIdNotAndIsDeletedFalse(code, couponId)) {
//            throw new ResourceAlreadyExistsException(
//                    COUPON_CODE_ALREADY_EXISTS.formatted(code)
//            );
//        }
//    }
//
//    private Promotion getPromotionOrThrow(UUID promotionId) {
//        return promotionRepository.findByIdAndIsDeletedFalse(promotionId)
//                .orElseThrow(() -> new ResourceNotFoundException(
//                        PROMOTION_NOT_FOUND_WITH_ID.formatted(promotionId)
//                ));
//    }
//
//    private Coupon getCouponOrThrow(UUID couponId) {
//        return couponRepository.findByIdAndIsDeletedFalse(couponId)
//                .orElseThrow(() -> new ResourceNotFoundException(
//                        COUPON_NOT_FOUND_WITH_ID.formatted(couponId)
//                ));
//    }
//}