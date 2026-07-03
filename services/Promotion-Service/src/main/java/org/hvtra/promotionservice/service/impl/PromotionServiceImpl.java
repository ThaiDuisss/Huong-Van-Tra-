package org.hvtra.promotionservice.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.exception.BusinessException;
import org.hvtra.commonservice.exception.ResourceNotFoundException;
import org.hvtra.promotionservice.dto.request.promotion.PromotionCreateRequest;
import org.hvtra.promotionservice.dto.request.promotion.PromotionFilterRequest;
import org.hvtra.promotionservice.dto.request.promotion.PromotionUpdateRequest;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.dto.response.PromotionResponse;
import org.hvtra.promotionservice.entity.Event;
import org.hvtra.promotionservice.entity.Promotion;
import org.hvtra.promotionservice.enums.promotion.PromotionStatus;
import org.hvtra.promotionservice.mapper.PromotionMapper;
import org.hvtra.promotionservice.repository.promotion.PromotionRepository;
import org.hvtra.promotionservice.repository.event.EventRepository;
import org.hvtra.promotionservice.service.PromotionService;
import org.hvtra.promotionservice.validation.validator.PromotionValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hvtra.promotionservice.constant.event.EventErrorMessage.EVENT_NOT_FOUND_WITH_ID;
import static org.hvtra.promotionservice.constant.promotion.PromotionErrorMessage.PROMOTION_NOT_FOUND_WITH_ID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PromotionServiceImpl implements PromotionService {

    PromotionRepository promotionRepository;
    EventRepository eventRepository;
    PromotionMapper promotionMapper;
    PromotionValidator promotionValidator;

    @Override
    @Transactional
    public PromotionResponse createPromotion(PromotionCreateRequest request) {
        promotionValidator.validateCreateRequest(request);

        return createPromotionInternal(request.getEventId(), request);
    }

    @Override
    @Transactional
    public PromotionResponse createPromotionByEvent(UUID eventId, PromotionCreateRequest request) {
        return createPromotionInternal(eventId, request);
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionResponse getPromotionById(UUID promotionId) {
        Promotion promotion = getExistingPromotion(promotionId);

        return promotionMapper.toResponse(promotion);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PromotionResponse> getPromotions(PromotionFilterRequest request, int page, int size) {
        PromotionFilterRequest filter = request == null ? new PromotionFilterRequest() : request;

        validatePromotionFilter(filter);

        Pageable pageable = PageRequest.of(page, size);

        Page<PromotionResponse> promotionPage = promotionRepository
                .searchPromotions(filter, pageable)
                .map(promotionMapper::toResponse);

        return PageResponse.from(promotionPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PromotionResponse> getPromotionsByEvent(UUID eventId, PromotionFilterRequest request, int page, int size) {
        getExistingEvent(eventId);

        PromotionFilterRequest filter = request == null ? new PromotionFilterRequest() : request;
        filter.setEventId(eventId);

        return getPromotions(filter, page, size);
    }

    @Override
    @Transactional
    public PromotionResponse updatePromotion(UUID promotionId, PromotionUpdateRequest request) {
        Promotion promotion = getExistingPromotion(promotionId);

        promotionValidator.validateBeforeUpdate(promotion);

        promotionMapper.updateEntityFromRequest(request, promotion);

        Promotion updatedPromotion = promotionRepository.save(promotion);

        return promotionMapper.toResponse(updatedPromotion);
    }

    @Override
    @Transactional
    public void deletePromotion(UUID promotionId) {
        Promotion promotion = getExistingPromotion(promotionId);

        promotionValidator.validateBeforeDelete(promotion);

        promotion.setIsDeleted(true);

        promotionRepository.save(promotion);
    }

    @Override
    @Transactional
    public PromotionResponse schedulePromotion(UUID promotionId) {
        return changeStatus(promotionId, PromotionStatus.SCHEDULED);
    }

    @Override
    @Transactional
    public PromotionResponse activatePromotion(UUID promotionId) {
        return changeStatus(promotionId, PromotionStatus.ACTIVE);
    }

    @Override
    @Transactional
    public PromotionResponse pausePromotion(UUID promotionId) {
        return changeStatus(promotionId, PromotionStatus.INACTIVE);
    }

    @Override
    @Transactional
    public PromotionResponse resumePromotion(UUID promotionId) {
        Promotion promotion = getExistingPromotion(promotionId);

        PromotionStatus targetStatus = promotionValidator.resolveResumeTargetStatus(promotion);

        promotionValidator.validateStatusTransition(promotion, targetStatus);

        return updateStatus(promotion, targetStatus);
    }

    @Override
    @Transactional
    public PromotionResponse cancelPromotion(UUID promotionId) {
        return changeStatus(promotionId, PromotionStatus.CANCELLED);
    }

    @Override
    @Transactional
    public PromotionResponse expirePromotion(UUID promotionId) {
        return changeStatus(promotionId, PromotionStatus.EXPIRED);
    }

    @Override
    @Transactional
    public PromotionResponse duplicatePromotion(UUID promotionId) {
        Promotion sourcePromotion = getExistingPromotion(promotionId);

        promotionValidator.validateBeforeDuplicate(sourcePromotion);

        Promotion duplicatedPromotion = promotionMapper.duplicateFrom(sourcePromotion);

        duplicatedPromotion.setStatus(PromotionStatus.DRAFT);
        duplicatedPromotion.setIsDeleted(false);

        Promotion savedPromotion = promotionRepository.save(duplicatedPromotion);

        return promotionMapper.toResponse(savedPromotion);
    }

    private PromotionResponse createPromotionInternal(UUID eventId, PromotionCreateRequest request) {
        Event event = getExistingEvent(eventId);

        promotionValidator.validateEventCanCreatePromotion(event);
        promotionValidator.validatePromotionTimeWithinEvent(request, event);

        Promotion promotion = promotionMapper.toEntity(request);

        promotion.setEvent(event);

        if (promotion.getStatus() == null) {
            promotion.setStatus(PromotionStatus.DRAFT);
        }

        if (promotion.getStackable() == null) {
            promotion.setStackable(false);
        }

        if (promotion.getPriority() == null) {
            promotion.setPriority(0);
        }

        Promotion savedPromotion = promotionRepository.save(promotion);

        return promotionMapper.toResponse(savedPromotion);
    }

    private PromotionResponse changeStatus(UUID promotionId, PromotionStatus targetStatus) {
        Promotion promotion = getExistingPromotion(promotionId);

        promotionValidator.validateStatusTransition(promotion, targetStatus);

        return updateStatus(promotion, targetStatus);
    }

    private PromotionResponse updateStatus(Promotion promotion, PromotionStatus targetStatus) {
        promotion.setStatus(targetStatus);

        Promotion updatedPromotion = promotionRepository.save(promotion);

        return promotionMapper.toResponse(updatedPromotion);
    }

    private Promotion getExistingPromotion(UUID promotionId) {
        return promotionRepository.findByIdAndIsDeletedFalse(promotionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        PROMOTION_NOT_FOUND_WITH_ID.formatted(promotionId)
                ));
    }

    private Event getExistingEvent(UUID eventId) {
        return eventRepository.findByIdAndIsDeletedFalse(eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        EVENT_NOT_FOUND_WITH_ID.formatted(eventId)
                ));
    }

    private void validatePromotionFilter(PromotionFilterRequest filter) {
        validateRange(
                filter.getStartFrom() != null
                        && filter.getStartTo() != null
                        && filter.getStartFrom().isAfter(filter.getStartTo()),
                "Start from time must be before or equal to start to time"
        );
        validateRange(
                filter.getEndFrom() != null
                        && filter.getEndTo() != null
                        && filter.getEndFrom().isAfter(filter.getEndTo()),
                "End from time must be before or equal to end to time"
        );
    }

    private void validateRange(boolean invalid, String message) {
        if (invalid) {
            throw new BusinessException(message);
        }
    }
}
