package org.hvtra.promotionservice.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.exception.ResourceNotFoundException;
import org.hvtra.promotionservice.dto.request.event.EventCreateRequest;
import org.hvtra.promotionservice.dto.request.event.EventStatusUpdateRequest;
import org.hvtra.promotionservice.dto.request.event.EventUpdateRequest;
import org.hvtra.promotionservice.dto.response.EventResponse;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.entity.Event;
import org.hvtra.promotionservice.enums.event.EventStatus;
import org.hvtra.promotionservice.mapper.EventMapper;
import org.hvtra.promotionservice.repository.EventRepository;
import org.hvtra.promotionservice.service.EventService;
import org.hvtra.promotionservice.validation.validator.EventValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    public PageResponse<EventResponse> getAllEvents(int page, int size) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<EventResponse> eventPage = eventRepository.findAllByIsDeletedFalse(pageable)
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
    public EventResponse updateEventStatus(UUID eventId, EventStatusUpdateRequest request) {
        Event event = getExistingEvent(eventId);

        eventValidator.validateStatusTransition(event, request);

        event.setStatus(request.getStatus());

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
    public EventResponse activateEvent(UUID eventId) {
        Event event = getExistingEvent(eventId);

        eventValidator.validateBeforeActivate(event);

        event.setStatus(EventStatus.ACTIVE);

        Event updatedEvent = eventRepository.save(event);

        return eventMapper.toResponse(updatedEvent);
    }

    @Override
    @Transactional
    public EventResponse cancelEvent(UUID eventId) {
        Event event = getExistingEvent(eventId);

        eventValidator.validateBeforeCancel(event);

        event.setStatus(EventStatus.CANCELLED);

        Event updatedEvent = eventRepository.save(event);

        return eventMapper.toResponse(updatedEvent);
    }

    @Override
    @Transactional
    public EventResponse endEvent(UUID eventId) {
        Event event = getExistingEvent(eventId);

        eventValidator.validateBeforeEnd(event);

        event.setStatus(EventStatus.ENDED);

        Event updatedEvent = eventRepository.save(event);

        return eventMapper.toResponse(updatedEvent);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EventResponse> getActiveEvents(int page, int size) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<EventResponse> eventPage = eventRepository
                .findAllByStatusAndIsDeletedFalse(EventStatus.ACTIVE, pageable)
                .map(eventMapper::toResponse);

        return PageResponse.from(eventPage);
    }

    private Event getExistingEvent(UUID eventId) {
        return eventRepository.findByIdAndIsDeletedFalse(eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        EVENT_NOT_FOUND_WITH_ID.formatted(eventId)
                ));
    }
}