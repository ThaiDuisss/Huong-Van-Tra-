package org.hvtra.promotionservice.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.exception.ResourceNotFoundException;
import org.hvtra.promotionservice.dto.request.promotion.PromotionCreateRequest;
import org.hvtra.promotionservice.dto.request.promotion.PromotionStatusUpdateRequest;
import org.hvtra.promotionservice.dto.request.promotion.PromotionUpdateRequest;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.dto.response.PromotionResponse;
import org.hvtra.promotionservice.entity.Event;
import org.hvtra.promotionservice.entity.Promotion;
import org.hvtra.promotionservice.enums.promotion.PromotionStatus;
import org.hvtra.promotionservice.mapper.PromotionMapper;
import org.hvtra.promotionservice.repository.EventRepository;
import org.hvtra.promotionservice.repository.PromotionRepository;
import org.hvtra.promotionservice.service.PromotionService;
import org.hvtra.promotionservice.validation.validator.PromotionValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    public PageResponse<PromotionResponse> getAllPromotions(int page, int size) {
        Pageable pageable = createPageable(page, size);

        Page<PromotionResponse> promotionPage = promotionRepository.findAllByIsDeletedFalse(pageable)
                .map(promotionMapper::toResponse);

        return PageResponse.from(promotionPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PromotionResponse> getPromotionsByEvent(UUID eventId, int page, int size) {
        getExistingEvent(eventId);

        Pageable pageable = createPageable(page, size);

        Page<PromotionResponse> promotionPage = promotionRepository
                .findAllByEventIdAndIsDeletedFalse(eventId, pageable)
                .map(promotionMapper::toResponse);

        return PageResponse.from(promotionPage);
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
    public PromotionResponse updatePromotionStatus(
            UUID promotionId,
            PromotionStatusUpdateRequest request
    ) {
        Promotion promotion = getExistingPromotion(promotionId);

        promotionValidator.validateStatusTransition(promotion, request);

        promotion.setStatus(request.getStatus());

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
    public PromotionResponse activatePromotion(UUID promotionId) {
        Promotion promotion = getExistingPromotion(promotionId);

        promotionValidator.validateBeforeActivate(promotion);

        promotion.setStatus(PromotionStatus.ACTIVE);

        Promotion updatedPromotion = promotionRepository.save(promotion);

        return promotionMapper.toResponse(updatedPromotion);
    }

    @Override
    @Transactional
    public PromotionResponse pausePromotion(UUID promotionId) {
        Promotion promotion = getExistingPromotion(promotionId);

        promotionValidator.validateBeforePause(promotion);

        promotion.setStatus(PromotionStatus.INACTIVE);

        Promotion updatedPromotion = promotionRepository.save(promotion);

        return promotionMapper.toResponse(updatedPromotion);
    }

    @Override
    @Transactional
    public PromotionResponse cancelPromotion(UUID promotionId) {
        Promotion promotion = getExistingPromotion(promotionId);

        promotionValidator.validateBeforeCancel(promotion);

        promotion.setStatus(PromotionStatus.CANCELLED);

        Promotion updatedPromotion = promotionRepository.save(promotion);

        return promotionMapper.toResponse(updatedPromotion);
    }

    @Override
    @Transactional
    public PromotionResponse expirePromotion(UUID promotionId) {
        Promotion promotion = getExistingPromotion(promotionId);

        promotionValidator.validateBeforeExpire(promotion);

        promotion.setStatus(PromotionStatus.EXPIRED);

        Promotion updatedPromotion = promotionRepository.save(promotion);

        return promotionMapper.toResponse(updatedPromotion);
    }

    @Override
    @Transactional
    public PromotionResponse duplicatePromotion(UUID promotionId) {
        Promotion sourcePromotion = getExistingPromotion(promotionId);

        promotionValidator.validateBeforeDuplicate(sourcePromotion);

        Promotion duplicatedPromotion = promotionMapper.duplicateFrom(sourcePromotion);

        Promotion savedPromotion = promotionRepository.save(duplicatedPromotion);

        return promotionMapper.toResponse(savedPromotion);
    }

    private PromotionResponse createPromotionInternal(UUID eventId, PromotionCreateRequest request) {
        Event event = getExistingEvent(eventId);

        promotionValidator.validateEventCanCreatePromotion(event);
        promotionValidator.validatePromotionTimeWithinEvent(request, event);

        Promotion promotion = promotionMapper.toEntity(request);
        promotion.setEvent(event);

        if (promotion.getStackable() == null) {
            promotion.setStackable(false);
        }

        if (promotion.getPriority() == null) {
            promotion.setPriority(0);
        }

        Promotion savedPromotion = promotionRepository.save(promotion);

        return promotionMapper.toResponse(savedPromotion);
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

    private Pageable createPageable(int page, int size) {
        return PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
    }
}