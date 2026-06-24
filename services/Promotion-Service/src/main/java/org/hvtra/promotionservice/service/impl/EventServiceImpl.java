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
import org.hvtra.promotionservice.mapper.EventMapper;
import org.hvtra.promotionservice.repository.EventRepository;
import org.hvtra.promotionservice.service.EventService;
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

    @Override
    @Transactional
    public EventResponse createEvent(EventCreateRequest request) {

        Event event = eventMapper.toEntity(request);

        event = eventRepository.save(event);

        return eventMapper.toResponse(event);
    }

    @Override
    @Transactional(readOnly = true)
    public EventResponse getEventById(UUID eventId) {
        Event event = eventRepository.findByIdAndIsDeletedFalse(eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        EVENT_NOT_FOUND_WITH_ID.formatted(eventId)
                ));

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
        Event event = eventRepository.findByIdAndIsDeletedFalse(eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        EVENT_NOT_FOUND_WITH_ID.formatted(eventId)
                ));

        eventMapper.updateEntityFromRequest(request, event);

        Event updatedEvent = eventRepository.save(event);

        return eventMapper.toResponse(updatedEvent);
    }

    @Override
    @Transactional
    public EventResponse updateEventStatus(UUID eventId, EventStatusUpdateRequest request) {
        Event event = eventRepository.findByIdAndIsDeletedFalse(eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        EVENT_NOT_FOUND_WITH_ID.formatted(eventId)
                ));

        event.setStatus(request.getStatus());

        Event updatedEvent = eventRepository.save(event);

        return eventMapper.toResponse(updatedEvent);
    }

    @Override
    @Transactional
    public void deleteEvent(UUID eventId) {
        Event event = eventRepository.findByIdAndIsDeletedFalse(eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        EVENT_NOT_FOUND_WITH_ID.formatted(eventId)
                ));

        event.setIsDeleted(true);

        eventRepository.save(event);
    }
}
