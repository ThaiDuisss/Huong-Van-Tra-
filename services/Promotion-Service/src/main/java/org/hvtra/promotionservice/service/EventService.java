package org.hvtra.promotionservice.service;

import org.hvtra.promotionservice.dto.request.event.EventCreateRequest;
import org.hvtra.promotionservice.dto.request.event.EventUpdateRequest;
import org.hvtra.promotionservice.dto.response.EventResponse;
import org.hvtra.promotionservice.dto.response.PageResponse;

import java.util.UUID;

public interface EventService {

    EventResponse createEvent(EventCreateRequest request);

    EventResponse getEventById(UUID eventId);

    PageResponse<EventResponse> getAllEvents(int page, int size);

    EventResponse updateEvent(UUID eventId, EventUpdateRequest request);

    void deleteEvent(UUID eventId);

    EventResponse scheduleEvent(UUID eventId);

    EventResponse activateEvent(UUID eventId);

    EventResponse pauseEvent(UUID eventId);

    EventResponse resumeEvent(UUID eventId);

    EventResponse cancelEvent(UUID eventId);

    EventResponse endEvent(UUID eventId);

    PageResponse<EventResponse> getActiveEvents(int page, int size);
}