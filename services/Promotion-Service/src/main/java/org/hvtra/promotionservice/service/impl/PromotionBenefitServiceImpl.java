//package org.hvtra.promotionservice.service.impl;
//
//import lombok.AccessLevel;
//import lombok.RequiredArgsConstructor;
//import lombok.experimental.FieldDefaults;
//import org.hvtra.commonservice.exception.ResourceNotFoundException;
//import org.hvtra.promotionservice.dto.request.promotionbenefit.PromotionBenefitCreateRequest;
//import org.hvtra.promotionservice.dto.request.promotionbenefit.PromotionBenefitUpdateRequest;
//import org.hvtra.promotionservice.dto.response.PageResponse;
//import org.hvtra.promotionservice.dto.response.PromotionBenefitResponse;
//import org.hvtra.promotionservice.entity.Promotion;
//import org.hvtra.promotionservice.entity.PromotionBenefit;
//import org.hvtra.promotionservice.mapper.PromotionBenefitMapper;
//import org.hvtra.promotionservice.repository.PromotionBenefitRepository;
//import org.hvtra.promotionservice.repository.PromotionRepository;
//import org.hvtra.promotionservice.service.PromotionBenefitService;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.data.domain.Pageable;
//import org.springframework.data.domain.Sort;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.UUID;
//
//import static org.hvtra.promotionservice.constant.promotion.PromotionErrorMessage.PROMOTION_NOT_FOUND_WITH_ID;
//import static org.hvtra.promotionservice.constant.promotionbenefit.PromotionBenefitErrorMessage.BENEFIT_NOT_FOUND_WITH_ID;
//
//@Service
//@RequiredArgsConstructor
//@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
//public class PromotionBenefitServiceImpl implements PromotionBenefitService {
//
//    PromotionRepository promotionRepository;
//    PromotionBenefitRepository promotionBenefitRepository;
//    PromotionBenefitMapper promotionBenefitMapper;
//
//    @Override
//    @Transactional
//    public PromotionBenefitResponse createBenefit(
//            UUID promotionId,
//            PromotionBenefitCreateRequest request
//    ) {
//        Promotion promotion = getPromotionOrThrow(promotionId);
//
//        PromotionBenefit benefit = promotionBenefitMapper.toEntity(request);
//        benefit.setPromotion(promotion);
//
//        PromotionBenefit savedBenefit = promotionBenefitRepository.save(benefit);
//
//        return promotionBenefitMapper.toResponse(savedBenefit);
//    }
//
//    @Override
//    @Transactional(readOnly = true)
//    public PageResponse<PromotionBenefitResponse> getBenefitsByPromotion(
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
//        Page<PromotionBenefitResponse> benefitPage =
//                promotionBenefitRepository.findAllByPromotionIdAndIsDeletedFalse(promotionId, pageable)
//                        .map(promotionBenefitMapper::toResponse);
//
//        return PageResponse.from(benefitPage);
//    }
//
//    @Override
//    @Transactional
//    public PromotionBenefitResponse updateBenefit(
//            UUID promotionId,
//            UUID benefitId,
//            PromotionBenefitUpdateRequest request
//    ) {
//        getPromotionOrThrow(promotionId);
//
//        PromotionBenefit benefit = getBenefitOrThrow(promotionId, benefitId);
//
//        promotionBenefitMapper.updateEntityFromRequest(request, benefit);
//
//        PromotionBenefit updatedBenefit = promotionBenefitRepository.save(benefit);
//
//        return promotionBenefitMapper.toResponse(updatedBenefit);
//    }
//
//    @Override
//    @Transactional
//    public void deleteBenefit(
//            UUID promotionId,
//            UUID benefitId
//    ) {
//        getPromotionOrThrow(promotionId);
//
//        PromotionBenefit benefit = getBenefitOrThrow(promotionId, benefitId);
//
//        benefit.setIsDeleted(true);
//
//        promotionBenefitRepository.save(benefit);
//    }
//
//    private Promotion getPromotionOrThrow(UUID promotionId) {
//        return promotionRepository.findByIdAndIsDeletedFalse(promotionId)
//                .orElseThrow(() -> new ResourceNotFoundException(
//                        PROMOTION_NOT_FOUND_WITH_ID.formatted(promotionId)
//                ));
//    }
//
//    private PromotionBenefit getBenefitOrThrow(
//            UUID promotionId,
//            UUID benefitId
//    ) {
//        return promotionBenefitRepository
//                .findByIdAndPromotionIdAndIsDeletedFalse(benefitId, promotionId)
//                .orElseThrow(() -> new ResourceNotFoundException(
//                        BENEFIT_NOT_FOUND_WITH_ID.formatted(benefitId)
//                ));
//    }
//}
