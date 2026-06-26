//package org.hvtra.promotionservice.service.impl;
//
//import lombok.AccessLevel;
//import lombok.RequiredArgsConstructor;
//import lombok.experimental.FieldDefaults;
//import org.hvtra.commonservice.exception.ResourceNotFoundException;
//import org.hvtra.promotionservice.dto.request.promotioncondition.PromotionConditionCreateRequest;
//import org.hvtra.promotionservice.dto.request.promotioncondition.PromotionConditionUpdateRequest;
//import org.hvtra.promotionservice.dto.response.PageResponse;
//import org.hvtra.promotionservice.dto.response.PromotionConditionResponse;
//import org.hvtra.promotionservice.entity.Promotion;
//import org.hvtra.promotionservice.entity.PromotionCondition;
//import org.hvtra.promotionservice.mapper.PromotionConditionMapper;
//import org.hvtra.promotionservice.repository.PromotionConditionRepository;
//import org.hvtra.promotionservice.repository.PromotionRepository;
//import org.hvtra.promotionservice.service.PromotionConditionService;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.data.domain.Pageable;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.UUID;
//
//import static org.hvtra.promotionservice.constant.promotion.PromotionErrorMessage.PROMOTION_NOT_FOUND_WITH_ID;
//
//@Service
//@RequiredArgsConstructor
//@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
//public class PromotionConditionServiceImpl implements PromotionConditionService {
//
//    PromotionRepository promotionRepository;
//    PromotionConditionRepository promotionConditionRepository;
//    PromotionConditionMapper promotionConditionMapper;
//
//    @Override
//    @Transactional
//    public PromotionConditionResponse createCondition(
//            UUID promotionId,
//            PromotionConditionCreateRequest request
//    ) {
//        Promotion promotion = findPromotionById(promotionId);
//
//        PromotionCondition promotionCondition = promotionConditionMapper.toEntity(request);
//        promotionCondition.setPromotion(promotion);
//
//        PromotionCondition savedPromotionCondition = promotionConditionRepository.save(promotionCondition);
//
//        return promotionConditionMapper.toResponse(savedPromotionCondition);
//    }
//
//    @Override
//    @Transactional(readOnly = true)
//    public PageResponse<PromotionConditionResponse> getConditionsByPromotion(
//            UUID promotionId,
//            int page,
//            int size
//    ) {
//        Promotion promotion = promotionRepository.findByIdAndIsDeletedFalse(promotionId)
//                .orElseThrow(() -> new ResourceNotFoundException(
//                        PROMOTION_NOT_FOUND_WITH_ID.formatted(promotionId)
//                ));
//
//        Pageable pageable = PageRequest.of(page, size);
//
//        Page<PromotionConditionResponse> conditionPage =
//                promotionConditionRepository.findByPromotionAndIsDeletedFalse(
//                        promotion,
//                        pageable
//                ).map(promotionConditionMapper::toResponse);
//
//        return PageResponse.from(conditionPage);
//    }
//
//    @Override
//    @Transactional
//    public PromotionConditionResponse updateCondition(
//            UUID promotionId,
//            UUID conditionId,
//            PromotionConditionUpdateRequest request
//    ) {
//        findPromotionById(promotionId);
//
//        PromotionCondition condition = findConditionInPromotion(promotionId, conditionId);
//
//        promotionConditionMapper.updateEntityFromRequest(request, condition);
//
//        PromotionCondition updatedCondition = promotionConditionRepository.save(condition);
//
//        return promotionConditionMapper.toResponse(updatedCondition);
//    }
//
//    @Override
//    @Transactional
//    public void deleteCondition(UUID promotionId, UUID conditionId) {
//        findPromotionById(promotionId);
//
//        PromotionCondition condition = findConditionInPromotion(promotionId, conditionId);
//
//        condition.setIsDeleted(true);
//
//        promotionConditionRepository.save(condition);
//    }
//
//    private Promotion findPromotionById(UUID promotionId) {
//        return promotionRepository.findByIdAndIsDeletedFalse(promotionId)
//                .orElseThrow(() -> new ResourceNotFoundException(
//                        PROMOTION_NOT_FOUND_WITH_ID.formatted(promotionId)
//                ));
//    }
//
//    private PromotionCondition findConditionInPromotion(UUID promotionId, UUID conditionId) {
//        return promotionConditionRepository
//                .findByIdAndPromotionIdAndIsDeletedFalse(conditionId, promotionId)
//                .orElseThrow(() -> new ResourceNotFoundException(
//                        PROMOTION_NOT_FOUND_WITH_ID.formatted(conditionId)
//                ));
//    }
//}
