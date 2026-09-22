package org.hvtra.promotionservice.service;

import org.hvtra.commonservice.common_core.exception.BusinessException;
import org.hvtra.commonservice.common_core.exception.ResourceNotFoundException;
import org.hvtra.promotionservice.dto.request.event.EventCreateRequest;
import org.hvtra.promotionservice.dto.request.event.EventExtendRequest;
import org.hvtra.promotionservice.dto.request.event.EventFilterRequest;
import org.hvtra.promotionservice.dto.request.event.EventUpdateRequest;
import org.hvtra.promotionservice.dto.response.EventResponse;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.entity.Event;
import org.hvtra.promotionservice.enums.event.EventAudience;
import org.hvtra.promotionservice.enums.event.EventStatus;
import org.hvtra.promotionservice.enums.event.EventType;
import org.hvtra.promotionservice.mapper.EventMapper;
import org.hvtra.promotionservice.repository.event.EventRepository;
import org.hvtra.promotionservice.service.impl.EventServiceImpl;
import org.hvtra.promotionservice.validation.validator.EventValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventServiceImpl")
class EventServiceImplTest {

    private static final UUID EVENT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final LocalDateTime NOW = LocalDateTime.now();
    private static final LocalDateTime PAST_START = NOW.minusDays(1);
    private static final LocalDateTime FUTURE_START = NOW.plusDays(1);
    private static final LocalDateTime FUTURE_END = NOW.plusDays(10);

    @Mock
    EventRepository eventRepository;

    @Mock
    EventMapper eventMapper;

    EventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventServiceImpl(
                eventRepository,
                eventMapper,
                new EventValidator()
        );
    }

    @Nested
    @DisplayName("createEvent")
    class CreateEvent {

        @Test
        @DisplayName("sets DRAFT when mapper returns event without status")
        void createEvent_setsDraftStatusByDefault() {
            EventCreateRequest request = createRequest();
            Event mappedEvent = event(null, FUTURE_START, FUTURE_END);
            Event savedEvent = event(EventStatus.DRAFT, FUTURE_START, FUTURE_END);
            EventResponse response = response(EventStatus.DRAFT);

            when(eventMapper.toEntity(request)).thenReturn(mappedEvent);
            when(eventRepository.save(mappedEvent)).thenReturn(savedEvent);
            when(eventMapper.toResponse(savedEvent)).thenReturn(response);

            EventResponse actual = eventService.createEvent(request);

            assertThat(actual.getStatus()).isEqualTo(EventStatus.DRAFT);
            assertThat(mappedEvent.getStatus()).isEqualTo(EventStatus.DRAFT);
            verify(eventRepository).save(mappedEvent);
        }

        @Test
        @DisplayName("keeps existing status from mapper when provided")
        void createEvent_keepsExistingStatus() {
            EventCreateRequest request = createRequest();
            Event mappedEvent = event(EventStatus.SCHEDULED, FUTURE_START, FUTURE_END);
            EventResponse response = response(EventStatus.SCHEDULED);

            when(eventMapper.toEntity(request)).thenReturn(mappedEvent);
            when(eventRepository.save(mappedEvent)).thenReturn(mappedEvent);
            when(eventMapper.toResponse(mappedEvent)).thenReturn(response);

            EventResponse actual = eventService.createEvent(request);

            assertThat(actual.getStatus()).isEqualTo(EventStatus.SCHEDULED);
            verify(eventRepository).save(mappedEvent);
        }
    }

    @Nested
    @DisplayName("getEventById")
    class GetEventById {

        @Test
        @DisplayName("returns event when it exists and is not deleted")
        void getEventById_returnsExistingEvent() {
            Event event = event(EventStatus.DRAFT, FUTURE_START, FUTURE_END);
            EventResponse response = response(EventStatus.DRAFT);

            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));
            when(eventMapper.toResponse(event)).thenReturn(response);

            EventResponse actual = eventService.getEventById(EVENT_ID);

            assertThat(actual).isSameAs(response);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when event is missing or deleted")
        void getEventById_throwsWhenMissing() {
            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.getEventById(EVENT_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(EVENT_ID.toString());
        }
    }

    @Nested
    @DisplayName("getEvents")
    class GetEvents {

        @Test
        @DisplayName("uses repository search and maps paginated result")
        void getEvents_searchesAndMapsPage() {
            EventFilterRequest filter = EventFilterRequest.builder()
                    .keyword("summer")
                    .status(EventStatus.ACTIVE)
                    .eventType(EventType.FLASH_SALE)
                    .audience(EventAudience.ALL_CUSTOMERS)
                    .startFrom(PAST_START)
                    .startTo(FUTURE_START)
                    .endFrom(PAST_START)
                    .endTo(FUTURE_END)
                    .build();
            Event event = event(EventStatus.ACTIVE, PAST_START, FUTURE_END);
            EventResponse response = response(EventStatus.ACTIVE);

            when(eventRepository.searchEvents(eq(filter), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(event)));
            when(eventMapper.toResponse(event)).thenReturn(response);

            PageResponse<EventResponse> actual = eventService.getEvents(filter, 0, 10);

            assertThat(actual.getContent()).containsExactly(response);
            assertThat(actual.getPage()).isZero();
            assertThat(actual.getSize()).isEqualTo(1);
        }

        @Test
        @DisplayName("creates empty filter when request is null")
        void getEvents_acceptsNullFilter() {
            Event event = event(EventStatus.DRAFT, FUTURE_START, FUTURE_END);
            EventResponse response = response(EventStatus.DRAFT);

            when(eventRepository.searchEvents(any(EventFilterRequest.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(event)));
            when(eventMapper.toResponse(event)).thenReturn(response);

            PageResponse<EventResponse> actual = eventService.getEvents(null, 0, 10);

            assertThat(actual.getContent()).containsExactly(response);
        }

        @Test
        @DisplayName("rejects invalid start range")
        void getEvents_rejectsInvalidStartRange() {
            EventFilterRequest filter = EventFilterRequest.builder()
                    .startFrom(FUTURE_START)
                    .startTo(PAST_START)
                    .build();

            assertThatThrownBy(() -> eventService.getEvents(filter, 0, 10))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Start from time");

            verify(eventRepository, never()).searchEvents(any(), any());
        }

        @Test
        @DisplayName("rejects invalid end range")
        void getEvents_rejectsInvalidEndRange() {
            EventFilterRequest filter = EventFilterRequest.builder()
                    .endFrom(FUTURE_END)
                    .endTo(PAST_START)
                    .build();

            assertThatThrownBy(() -> eventService.getEvents(filter, 0, 10))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("End from time");

            verify(eventRepository, never()).searchEvents(any(), any());
        }
    }

    @Nested
    @DisplayName("updateEvent")
    class UpdateEvent {

        @Test
        @DisplayName("updates editable DRAFT event")
        void updateEvent_updatesDraftEvent() {
            Event event = event(EventStatus.DRAFT, FUTURE_START, FUTURE_END);
            EventUpdateRequest request = updateRequest();
            EventResponse response = response(EventStatus.DRAFT);

            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));
            when(eventRepository.save(event)).thenReturn(event);
            when(eventMapper.toResponse(event)).thenReturn(response);

            EventResponse actual = eventService.updateEvent(EVENT_ID, request);

            assertThat(actual).isSameAs(response);
            verify(eventMapper).updateEntityFromRequest(request, event);
            verify(eventRepository).save(event);
        }

        @Test
        @DisplayName("rejects update for ACTIVE event")
        void updateEvent_rejectsActiveEvent() {
            Event event = event(EventStatus.ACTIVE, PAST_START, FUTURE_END);

            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));

            assertThatThrownBy(() -> eventService.updateEvent(EVENT_ID, updateRequest()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Active event cannot be updated");

            verify(eventMapper, never()).updateEntityFromRequest(any(), any());
            verify(eventRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteEvent")
    class DeleteEvent {

        @Test
        @DisplayName("soft deletes DRAFT event")
        void deleteEvent_softDeletesDraftEvent() {
            Event event = event(EventStatus.DRAFT, FUTURE_START, FUTURE_END);

            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));
            when(eventRepository.save(event)).thenReturn(event);

            eventService.deleteEvent(EVENT_ID);

            assertThat(event.getIsDeleted()).isTrue();
            verify(eventRepository).save(event);
        }

        @Test
        @DisplayName("rejects delete for ACTIVE event")
        void deleteEvent_rejectsActiveEvent() {
            Event event = event(EventStatus.ACTIVE, PAST_START, FUTURE_END);

            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));

            assertThatThrownBy(() -> eventService.deleteEvent(EVENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Active event cannot be deleted");

            verify(eventRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("status lifecycle")
    class StatusLifecycle {

        @Test
        @DisplayName("schedules DRAFT event with future start time")
        void scheduleEvent_schedulesDraftEvent() {
            assertStatusChange(EventStatus.DRAFT, EventStatus.SCHEDULED, FUTURE_START, FUTURE_END,
                    () -> eventService.scheduleEvent(EVENT_ID));
        }

        @Test
        @DisplayName("rejects scheduling event that already started")
        void scheduleEvent_rejectsStartedEvent() {
            Event event = event(EventStatus.DRAFT, PAST_START, FUTURE_END);

            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));

            assertThatThrownBy(() -> eventService.scheduleEvent(EVENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Start time must be in the future");

            verify(eventRepository, never()).save(any());
        }

        @Test
        @DisplayName("activates SCHEDULED event that has started and not ended")
        void activateEvent_activatesScheduledEvent() {
            assertStatusChange(EventStatus.SCHEDULED, EventStatus.ACTIVE, PAST_START, FUTURE_END,
                    () -> eventService.activateEvent(EVENT_ID));
        }

        @Test
        @DisplayName("rejects activation before start time")
        void activateEvent_rejectsBeforeStartTime() {
            Event event = event(EventStatus.SCHEDULED, FUTURE_START, FUTURE_END);

            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));

            assertThatThrownBy(() -> eventService.activateEvent(EVENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Event has not started yet");

            verify(eventRepository, never()).save(any());
        }

        @Test
        @DisplayName("pauses ACTIVE event")
        void pauseEvent_pausesActiveEvent() {
            assertStatusChange(EventStatus.ACTIVE, EventStatus.PAUSED, PAST_START, FUTURE_END,
                    () -> eventService.pauseEvent(EVENT_ID));
        }

        @Test
        @DisplayName("resumes PAUSED event to ACTIVE when start time has passed")
        void resumeEvent_returnsActiveWhenStarted() {
            assertStatusChange(EventStatus.PAUSED, EventStatus.ACTIVE, PAST_START, FUTURE_END,
                    () -> eventService.resumeEvent(EVENT_ID));
        }

        @Test
        @DisplayName("resumes PAUSED event to SCHEDULED when start time is still in future")
        void resumeEvent_returnsScheduledWhenFutureStart() {
            assertStatusChange(EventStatus.PAUSED, EventStatus.SCHEDULED, FUTURE_START, FUTURE_END,
                    () -> eventService.resumeEvent(EVENT_ID));
        }

        @Test
        @DisplayName("cancels ACTIVE event")
        void cancelEvent_cancelsActiveEvent() {
            assertStatusChange(EventStatus.ACTIVE, EventStatus.CANCELLED, PAST_START, FUTURE_END,
                    () -> eventService.cancelEvent(EVENT_ID));
        }

        @Test
        @DisplayName("ends ACTIVE event")
        void endEvent_endsActiveEvent() {
            assertStatusChange(EventStatus.ACTIVE, EventStatus.ENDED, PAST_START, FUTURE_END,
                    () -> eventService.endEvent(EVENT_ID));
        }

        @Test
        @DisplayName("rejects any status change from CANCELLED")
        void changeStatus_rejectsCancelledEvent() {
            Event event = event(EventStatus.CANCELLED, PAST_START, FUTURE_END);

            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));

            assertThatThrownBy(() -> eventService.activateEvent(EVENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cancelled event cannot change status");

            verify(eventRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("extendEvent")
    class ExtendEvent {

        @Test
        @DisplayName("extends ACTIVE event when new end time is after current end time")
        void extendEvent_updatesEndTime() {
            Event event = event(EventStatus.ACTIVE, PAST_START, FUTURE_END);
            LocalDateTime newEndAt = FUTURE_END.plusDays(3);
            EventExtendRequest request = EventExtendRequest.builder()
                    .endAt(newEndAt)
                    .build();
            EventResponse response = response(EventStatus.ACTIVE);

            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));
            when(eventRepository.save(event)).thenReturn(event);
            when(eventMapper.toResponse(event)).thenReturn(response);

            EventResponse actual = eventService.extendEvent(EVENT_ID, request);

            assertThat(actual).isSameAs(response);
            assertThat(event.getEndAt()).isEqualTo(newEndAt);
            assertThat(event.getStatus()).isEqualTo(EventStatus.ACTIVE);
        }

        @Test
        @DisplayName("reactivates EXPIRED event after valid extension")
        void extendEvent_reactivatesExpiredEvent() {
            Event event = event(EventStatus.EXPIRED, PAST_START, NOW.minusHours(1));
            LocalDateTime newEndAt = FUTURE_END.plusDays(3);
            EventExtendRequest request = EventExtendRequest.builder()
                    .endAt(newEndAt)
                    .build();
            EventResponse response = response(EventStatus.ACTIVE);

            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));
            when(eventRepository.save(event)).thenReturn(event);
            when(eventMapper.toResponse(event)).thenReturn(response);

            EventResponse actual = eventService.extendEvent(EVENT_ID, request);

            assertThat(actual).isSameAs(response);
            assertThat(event.getEndAt()).isEqualTo(newEndAt);
            assertThat(event.getStatus()).isEqualTo(EventStatus.ACTIVE);
        }

        @Test
        @DisplayName("rejects extension when new end time is not after current end time")
        void extendEvent_rejectsNonIncreasingEndTime() {
            Event event = event(EventStatus.ACTIVE, PAST_START, FUTURE_END);
            EventExtendRequest request = EventExtendRequest.builder()
                    .endAt(FUTURE_END)
                    .build();

            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));

            assertThatThrownBy(() -> eventService.extendEvent(EVENT_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("after current end time");

            verify(eventRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getActiveEvents")
    class GetActiveEvents {

        @Test
        @DisplayName("queries only ACTIVE and non-deleted events")
        void getActiveEvents_queriesActiveStatusOnly() {
            Event event = event(EventStatus.ACTIVE, PAST_START, FUTURE_END);
            EventResponse response = response(EventStatus.ACTIVE);

            when(eventRepository.findAllByStatusAndIsDeletedFalse(eq(EventStatus.ACTIVE), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(event)));
            when(eventMapper.toResponse(event)).thenReturn(response);

            PageResponse<EventResponse> actual = eventService.getActiveEvents(0, 20);

            assertThat(actual.getContent()).containsExactly(response);
            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(eventRepository).findAllByStatusAndIsDeletedFalse(eq(EventStatus.ACTIVE), pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
            assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
        }
    }

    private void assertStatusChange(
            EventStatus currentStatus,
            EventStatus targetStatus,
            LocalDateTime startAt,
            LocalDateTime endAt,
            StatusAction action
    ) {
        Event event = event(currentStatus, startAt, endAt);
        EventResponse response = response(targetStatus);

        when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventRepository.save(event)).thenReturn(event);
        when(eventMapper.toResponse(event)).thenReturn(response);

        EventResponse actual = action.execute();

        assertThat(actual).isSameAs(response);
        assertThat(event.getStatus()).isEqualTo(targetStatus);
        verify(eventRepository).save(event);
    }

    private EventCreateRequest createRequest() {
        return EventCreateRequest.builder()
                .name("Tet Holiday Campaign 2027")
                .description("Campaign for all customers")
                .startAt(FUTURE_START)
                .endAt(FUTURE_END)
                .eventType(EventType.SEASONAL_CAMPAIGN)
                .audience(EventAudience.ALL_CUSTOMERS)
                .build();
    }

    private EventUpdateRequest updateRequest() {
        return EventUpdateRequest.builder()
                .name("Updated Campaign")
                .description("Updated description")
                .startAt(FUTURE_START)
                .endAt(FUTURE_END)
                .eventType(EventType.FLASH_SALE)
                .audience(EventAudience.CUSTOMER_SEGMENT)
                .build();
    }

    private Event event(EventStatus status, LocalDateTime startAt, LocalDateTime endAt) {
        Event event = Event.builder()
                .name("Tet Holiday Campaign 2027")
                .description("Campaign for all customers")
                .startAt(startAt)
                .endAt(endAt)
                .eventType(EventType.SEASONAL_CAMPAIGN)
                .audience(EventAudience.ALL_CUSTOMERS)
                .status(status)
                .build();
        event.setId(EVENT_ID);
        event.setIsDeleted(false);
        return event;
    }

    private EventResponse response(EventStatus status) {
        return EventResponse.builder()
                .id(EVENT_ID)
                .name("Tet Holiday Campaign 2027")
                .description("Campaign for all customers")
                .startAt(FUTURE_START)
                .endAt(FUTURE_END)
                .eventType(EventType.SEASONAL_CAMPAIGN)
                .audience(EventAudience.ALL_CUSTOMERS)
                .status(status)
                .isDeleted(false)
                .build();
    }

    @FunctionalInterface
    private interface StatusAction {
        EventResponse execute();
    }
}
