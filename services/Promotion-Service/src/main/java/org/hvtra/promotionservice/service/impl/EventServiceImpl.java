package org.hvtra.promotionservice.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.exception.BusinessException;
import org.hvtra.commonservice.exception.ResourceNotFoundException;
import org.hvtra.promotionservice.dto.request.event.EventCreateRequest;
import org.hvtra.promotionservice.dto.request.event.EventExtendRequest;
import org.hvtra.promotionservice.dto.request.event.EventFilterRequest;
import org.hvtra.promotionservice.dto.request.event.EventUpdateRequest;
import org.hvtra.promotionservice.dto.response.EventResponse;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.entity.Event;
import org.hvtra.promotionservice.enums.event.EventStatus;
import org.hvtra.promotionservice.mapper.EventMapper;
import org.hvtra.promotionservice.repository.event.EventRepository;
import org.hvtra.promotionservice.service.EventService;
import org.hvtra.promotionservice.util.PageUtils;
import org.hvtra.promotionservice.validation.validator.EventValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hvtra.promotionservice.constant.event.EventErrorMessage.EVENT_NOT_FOUND_WITH_ID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EventServiceImpl implements EventService {

    EventRepository eventRepository;
    EventMapper eventMapper;
    EventValidator eventValidator;

    @Override
    @Transactional
    public EventResponse createEvent(EventCreateRequest request) {
        Event event = eventMapper.toEntity(request);

        if (event.getStatus() == null) {
            event.setStatus(EventStatus.DRAFT);
        }

        Event savedEvent = eventRepository.save(event);

        return eventMapper.toResponse(savedEvent);
    }

    @Override
    @Transactional(readOnly = true)
    public EventResponse getEventById(UUID eventId) {
        Event event = getExistingEvent(eventId);

        return eventMapper.toResponse(event);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EventResponse> getEvents(EventFilterRequest request, int page, int size) {
        EventFilterRequest filter = request == null ? new EventFilterRequest() : request;

        validateEventFilter(filter);

        Pageable pageable = PageRequest.of(page, size);

        Page<EventResponse> eventPage = eventRepository
                .searchEvents(filter, pageable)
                .map(eventMapper::toResponse);

        return PageResponse.from(eventPage);
    }

    @Override
    @Transactional
    public EventResponse updateEvent(UUID eventId, EventUpdateRequest request) {
        Event event = getExistingEvent(eventId);

        eventValidator.validateBeforeUpdate(event);

        eventMapper.updateEntityFromRequest(request, event);

        Event updatedEvent = eventRepository.save(event);

        return eventMapper.toResponse(updatedEvent);
    }

    @Override
    @Transactional
    public void deleteEvent(UUID eventId) {
        Event event = getExistingEvent(eventId);

        eventValidator.validateBeforeDelete(event);

        event.setIsDeleted(true);

        eventRepository.save(event);
    }

    @Override
    @Transactional
    public EventResponse scheduleEvent(UUID eventId) {
        return changeStatus(eventId, EventStatus.SCHEDULED);
    }

    @Override
    @Transactional
    public EventResponse activateEvent(UUID eventId) {
        return changeStatus(eventId, EventStatus.ACTIVE);
    }

    @Override
    @Transactional
    public EventResponse pauseEvent(UUID eventId) {
        return changeStatus(eventId, EventStatus.PAUSED);
    }

    @Override
    @Transactional
    public EventResponse resumeEvent(UUID eventId) {
        Event event = getExistingEvent(eventId);

        EventStatus targetStatus = eventValidator.resolveResumeTargetStatus(event);

        eventValidator.validateStatusTransition(event, targetStatus);

        return updateStatus(event, targetStatus);
    }

    @Override
    @Transactional
    public EventResponse cancelEvent(UUID eventId) {
        return changeStatus(eventId, EventStatus.CANCELLED);
    }

    @Override
    @Transactional
    public EventResponse endEvent(UUID eventId) {
        return changeStatus(eventId, EventStatus.ENDED);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EventResponse> getActiveEvents(int page, int size) {
        Pageable pageable = PageUtils.createPageable(page, size);

        Page<EventResponse> eventPage = eventRepository
                .findAllByStatusAndIsDeletedFalse(EventStatus.ACTIVE, pageable)
                .map(eventMapper::toResponse);

        return PageResponse.from(eventPage);
    }

    @Override
    @Transactional
    public EventResponse extendEvent(UUID eventId, EventExtendRequest request) {
        Event event = getExistingEvent(eventId);

        eventValidator.validateCanExtend(event, request.getEndAt());

        event.setEndAt(request.getEndAt());

        if (event.getStatus() == EventStatus.EXPIRED) {
            eventValidator.validateStatusTransition(event, EventStatus.ACTIVE);
            event.setStatus(EventStatus.ACTIVE);
        }

        Event updatedEvent = eventRepository.save(event);

        return eventMapper.toResponse(updatedEvent);
    }

    private EventResponse changeStatus(UUID eventId, EventStatus targetStatus) {
        Event event = getExistingEvent(eventId);

        eventValidator.validateStatusTransition(event, targetStatus);

        return updateStatus(event, targetStatus);
    }

    private EventResponse updateStatus(Event event, EventStatus targetStatus) {
        event.setStatus(targetStatus);

        Event updatedEvent = eventRepository.save(event);

        return eventMapper.toResponse(updatedEvent);
    }

    private Event getExistingEvent(UUID eventId) {
        return eventRepository.findByIdAndIsDeletedFalse(eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        EVENT_NOT_FOUND_WITH_ID.formatted(eventId)
                ));
    }

    private void validateEventFilter(EventFilterRequest filter) {
        if (filter.getStartFrom() != null
                && filter.getStartTo() != null
                && filter.getStartFrom().isAfter(filter.getStartTo())) {
            throw new BusinessException("Start from time must be before or equal to start to time");
        }

        if (filter.getEndFrom() != null
                && filter.getEndTo() != null
                && filter.getEndFrom().isAfter(filter.getEndTo())) {
            throw new BusinessException("End from time must be before or equal to end to time");
        }
    }
}