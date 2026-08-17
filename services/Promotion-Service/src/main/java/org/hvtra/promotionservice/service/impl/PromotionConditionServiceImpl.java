package org.hvtra.promotionservice.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.exception.ResourceNotFoundException;
import org.hvtra.promotionservice.dto.request.promotioncondition.PromotionConditionCreateRequest;
import org.hvtra.promotionservice.dto.request.promotioncondition.PromotionConditionUpdateRequest;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.dto.response.PromotionConditionResponse;
import org.hvtra.promotionservice.entity.Promotion;
import org.hvtra.promotionservice.entity.PromotionCondition;
import org.hvtra.promotionservice.mapper.PromotionConditionMapper;
import org.hvtra.promotionservice.repository.PromotionConditionRepository;
import org.hvtra.promotionservice.repository.promotion.PromotionRepository;
import org.hvtra.promotionservice.service.PromotionConditionService;
import org.hvtra.promotionservice.validation.validator.PromotionConditionValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hvtra.promotionservice.constant.promotion.PromotionErrorMessage.PROMOTION_NOT_FOUND_WITH_ID;
import static org.hvtra.promotionservice.constant.promotioncondition.PromotionConditionErrorMessage.CONDITION_NOT_FOUND_WITH_ID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PromotionConditionServiceImpl implements PromotionConditionService {

    PromotionRepository promotionRepository;
    PromotionConditionRepository promotionConditionRepository;
    PromotionConditionMapper promotionConditionMapper;
    PromotionConditionValidator promotionConditionValidator;

    @Override
    @Transactional
    public PromotionConditionResponse createCondition(
            UUID promotionId,
            PromotionConditionCreateRequest request
    ) {
        Promotion promotion = getExistingPromotion(promotionId);

        promotionConditionValidator.validateBeforeCreate(promotion, request);

        PromotionCondition condition = promotionConditionMapper.toEntity(request);
        condition.setPromotion(promotion);

        PromotionCondition savedCondition = promotionConditionRepository.save(condition);

        return promotionConditionMapper.toResponse(savedCondition);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PromotionConditionResponse> getConditionsByPromotion(
            UUID promotionId,
            int page,
            int size
    ) {
        Promotion promotion = getExistingPromotion(promotionId);

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<PromotionConditionResponse> conditionPage =
                promotionConditionRepository.findByPromotionAndIsDeletedFalse(promotion, pageable)
                        .map(promotionConditionMapper::toResponse);

        return PageResponse.from(conditionPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionConditionResponse getConditionById(UUID promotionId, UUID conditionId) {
        getExistingPromotion(promotionId);

        PromotionCondition condition = getExistingCondition(promotionId, conditionId);

        return promotionConditionMapper.toResponse(condition);
    }

    @Override
    @Transactional
    public PromotionConditionResponse updateCondition(
            UUID promotionId,
            UUID conditionId,
            PromotionConditionUpdateRequest request
    ) {
        Promotion promotion = getExistingPromotion(promotionId);

        promotionConditionValidator.validateBeforeUpdate(promotion, request);

        PromotionCondition condition = getExistingCondition(promotionId, conditionId);

        promotionConditionMapper.updateEntityFromRequest(request, condition);

        PromotionCondition updatedCondition = promotionConditionRepository.save(condition);

        return promotionConditionMapper.toResponse(updatedCondition);
    }

    @Override
    @Transactional
    public void deleteCondition(UUID promotionId, UUID conditionId) {
        Promotion promotion = getExistingPromotion(promotionId);

        promotionConditionValidator.validateBeforeDelete(promotion);

        PromotionCondition condition = getExistingCondition(promotionId, conditionId);

        condition.setIsDeleted(true);

        promotionConditionRepository.save(condition);
    }

    private Promotion getExistingPromotion(UUID promotionId) {
        return promotionRepository.findByIdAndIsDeletedFalse(promotionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        PROMOTION_NOT_FOUND_WITH_ID.formatted(promotionId)
                ));
    }

    private PromotionCondition getExistingCondition(UUID promotionId, UUID conditionId) {
        return promotionConditionRepository
                .findByIdAndPromotionIdAndIsDeletedFalse(conditionId, promotionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        CONDITION_NOT_FOUND_WITH_ID.formatted(conditionId)
                ));
    }
}
