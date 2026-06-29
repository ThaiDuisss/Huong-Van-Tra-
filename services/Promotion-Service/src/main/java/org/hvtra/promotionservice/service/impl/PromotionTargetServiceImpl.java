package org.hvtra.promotionservice.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.exception.ResourceNotFoundException;
import org.hvtra.promotionservice.dto.request.promotiontarget.PromotionTargetCreateRequest;
import org.hvtra.promotionservice.dto.request.promotiontarget.PromotionTargetUpdateRequest;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.dto.response.PromotionTargetResponse;
import org.hvtra.promotionservice.entity.Promotion;
import org.hvtra.promotionservice.entity.PromotionTarget;
import org.hvtra.promotionservice.mapper.PromotionTargetMapper;
import org.hvtra.promotionservice.repository.PromotionRepository;
import org.hvtra.promotionservice.repository.PromotionTargetRepository;
import org.hvtra.promotionservice.service.PromotionTargetService;
import org.hvtra.promotionservice.validation.validator.PromotionTargetValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hvtra.promotionservice.constant.promotion.PromotionErrorMessage.PROMOTION_NOT_FOUND_WITH_ID;
import static org.hvtra.promotionservice.constant.promotiontarget.PromotionTargetErrorMessage.TARGET_NOT_FOUND_WITH_ID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PromotionTargetServiceImpl implements PromotionTargetService {

    PromotionRepository promotionRepository;
    PromotionTargetRepository promotionTargetRepository;
    PromotionTargetMapper promotionTargetMapper;
    PromotionTargetValidator promotionTargetValidator;

    @Override
    @Transactional
    public PromotionTargetResponse createTarget(
            UUID promotionId,
            PromotionTargetCreateRequest request
    ) {
        Promotion promotion = getExistingPromotion(promotionId);

        promotionTargetValidator.validateBeforeCreate(promotion, request);

        PromotionTarget target = promotionTargetMapper.toEntity(request);
        target.setPromotion(promotion);

        if (target.getExcluded() == null) {
            target.setExcluded(false);
        }

        PromotionTarget savedTarget = promotionTargetRepository.save(target);

        return promotionTargetMapper.toResponse(savedTarget);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PromotionTargetResponse> getTargetsByPromotion(
            UUID promotionId,
            int page,
            int size
    ) {
        getExistingPromotion(promotionId);

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<PromotionTargetResponse> targetPage =
                promotionTargetRepository.findAllByPromotionIdAndIsDeletedFalse(promotionId, pageable)
                        .map(promotionTargetMapper::toResponse);

        return PageResponse.from(targetPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionTargetResponse getTargetById(UUID promotionId, UUID targetId) {
        getExistingPromotion(promotionId);

        PromotionTarget target = getExistingTarget(promotionId, targetId);

        return promotionTargetMapper.toResponse(target);
    }

    @Override
    @Transactional
    public PromotionTargetResponse updateTarget(
            UUID promotionId,
            UUID targetId,
            PromotionTargetUpdateRequest request
    ) {
        Promotion promotion = getExistingPromotion(promotionId);

        promotionTargetValidator.validateBeforeUpdate(promotion, targetId, request);

        PromotionTarget target = getExistingTarget(promotionId, targetId);

        promotionTargetMapper.updateEntityFromRequest(request, target);

        if (target.getExcluded() == null) {
            target.setExcluded(false);
        }

        PromotionTarget updatedTarget = promotionTargetRepository.save(target);

        return promotionTargetMapper.toResponse(updatedTarget);
    }

    @Override
    @Transactional
    public void deleteTarget(UUID promotionId, UUID targetId) {
        Promotion promotion = getExistingPromotion(promotionId);

        promotionTargetValidator.validateBeforeDelete(promotion);

        PromotionTarget target = getExistingTarget(promotionId, targetId);

        target.setIsDeleted(true);

        promotionTargetRepository.save(target);
    }

    private Promotion getExistingPromotion(UUID promotionId) {
        return promotionRepository.findByIdAndIsDeletedFalse(promotionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        PROMOTION_NOT_FOUND_WITH_ID.formatted(promotionId)
                ));
    }

    private PromotionTarget getExistingTarget(UUID promotionId, UUID targetId) {
        return promotionTargetRepository.findByIdAndPromotionIdAndIsDeletedFalse(targetId, promotionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        TARGET_NOT_FOUND_WITH_ID.formatted(targetId)
                ));
    }
}