package org.hvtra.promotionservice.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.exception.ResourceNotFoundException;
import org.hvtra.promotionservice.dto.request.eventpromotion.EventPromotionRequest;
import org.hvtra.promotionservice.dto.request.promotion.PromotionCreateRequest;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.dto.response.PromotionResponse;
import org.hvtra.promotionservice.entity.Event;
import org.hvtra.promotionservice.entity.Promotion;
import org.hvtra.promotionservice.mapper.PromotionMapper;
import org.hvtra.promotionservice.repository.EventRepository;
import org.hvtra.promotionservice.repository.PromotionRepository;
import org.hvtra.promotionservice.service.EventPromotionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static org.hvtra.promotionservice.constant.event.EventErrorMessage.EVENT_NOT_FOUND_WITH_ID;
import static org.hvtra.promotionservice.constant.promotion.PromotionErrorMessage.PROMOTION_NOT_FOUND_WITH_ID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EventPromotionServiceImpl implements EventPromotionService {

    EventRepository eventRepository;
    PromotionRepository promotionRepository;
    PromotionMapper promotionMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PromotionResponse> getPromotionsByEvent(
            UUID eventId,
            int page,
            int size
    ) {
        Event event = getEventById(eventId);

        Pageable pageable = PageRequest.of(page, size);

        Page<PromotionResponse> promotionPage = promotionRepository
                .findByEventAndIsDeletedFalse(event, pageable)
                .map(promotionMapper::toResponse);

        return PageResponse.from(promotionPage);
    }

    @Override
    @Transactional
    public PromotionResponse createPromotionInEvent(
            UUID eventId,
            PromotionCreateRequest request
    ) {
        Event event = getEventById(eventId);

        Promotion promotion = promotionMapper.toEntity(request);
        promotion.setEvent(event);

        Promotion savedPromotion = promotionRepository.save(promotion);

        return promotionMapper.toResponse(savedPromotion);
    }

    @Override
    @Transactional
    public void attachPromotionsToEvent(
            UUID eventId,
            EventPromotionRequest request
    ) {
        Event event = getEventById(eventId);

        List<Promotion> promotions =
                promotionRepository.findAllById(request.getPromotionIds());

        validatePromotionIdsExist(request.getPromotionIds(), promotions);

        promotions.forEach(promotion -> promotion.setEvent(event));

        promotionRepository.saveAll(promotions);
    }

    @Override
    @Transactional
    public void detachPromotionsFromEvent(
            UUID eventId,
            EventPromotionRequest request
    ) {
        Event event = getEventById(eventId);

        List<Promotion> promotions =
                promotionRepository.findAllById(request.getPromotionIds());

        validatePromotionIdsExist(request.getPromotionIds(), promotions);

        promotions.forEach(promotion -> {
            if (event.equals(promotion.getEvent())) {
                promotion.setEvent(null);
            }
        });

        promotionRepository.saveAll(promotions);
    }

    private Event getEventById(UUID eventId) {
        return eventRepository.findByIdAndIsDeletedFalse(eventId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(EVENT_NOT_FOUND_WITH_ID.formatted(eventId))
                );
    }

    private void validatePromotionIdsExist(
            List<UUID> requestPromotionIds,
            List<Promotion> promotions
    ) {
        if (requestPromotionIds.size() != promotions.size()) {
            throw new ResourceNotFoundException(PROMOTION_NOT_FOUND_WITH_ID.formatted(requestPromotionIds));
        }
    }
}
