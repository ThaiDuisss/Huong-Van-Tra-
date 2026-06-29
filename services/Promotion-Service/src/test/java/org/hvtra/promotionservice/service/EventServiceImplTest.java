package org.hvtra.promotionservice.service;

import org.hvtra.commonservice.exception.BusinessException;
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
import org.hvtra.promotionservice.service.impl.EventServiceImpl;
import org.hvtra.promotionservice.validation.validator.EventValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventServiceImpl Unit Tests")
class EventServiceImplTest {

    private static final UUID EVENT_ID = UUID.randomUUID();

    @Mock
    EventRepository eventRepository;

    @Mock
    EventMapper eventMapper;

    @Mock
    EventValidator eventValidator;

    @InjectMocks
    EventServiceImpl eventService;

    @Nested
    @DisplayName("createEvent")
    class CreateEventTest {

        @Test
        @DisplayName("Should create event successfully")
        void shouldCreateEventSuccessfully() {
            EventCreateRequest request = mock(EventCreateRequest.class);
            Event event = new Event();
            Event savedEvent = new Event();
            EventResponse expectedResponse = mock(EventResponse.class);

            when(eventMapper.toEntity(request)).thenReturn(event);
            when(eventRepository.save(event)).thenReturn(savedEvent);
            when(eventMapper.toResponse(savedEvent)).thenReturn(expectedResponse);

            EventResponse actualResponse = eventService.createEvent(request);

            assertThat(actualResponse).isSameAs(expectedResponse);

            verify(eventMapper).toEntity(request);
            verify(eventRepository).save(event);
            verify(eventMapper).toResponse(savedEvent);
            verifyNoInteractions(eventValidator);
        }
    }

    @Nested
    @DisplayName("getEventById")
    class GetEventByIdTest {

        @Test
        @DisplayName("Should get event by id successfully")
        void shouldGetEventByIdSuccessfully() {
            Event event = new Event();
            EventResponse expectedResponse = mock(EventResponse.class);

            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID))
                    .thenReturn(Optional.of(event));
            when(eventMapper.toResponse(event)).thenReturn(expectedResponse);

            EventResponse actualResponse = eventService.getEventById(EVENT_ID);

            assertThat(actualResponse).isSameAs(expectedResponse);

            verify(eventRepository).findByIdAndIsDeletedFalse(EVENT_ID);
            verify(eventMapper).toResponse(event);
            verifyNoInteractions(eventValidator);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when event not found")
        void shouldThrowResourceNotFoundExceptionWhenEventNotFound() {
            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.getEventById(EVENT_ID))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(eventRepository).findByIdAndIsDeletedFalse(EVENT_ID);
            verifyNoInteractions(eventMapper, eventValidator);
        }
    }

    @Nested
    @DisplayName("getAllEvents")
    class GetAllEventsTest {

        @Test
        @DisplayName("Should get paginated non-deleted events sorted by createdAt desc")
        void shouldGetAllEventsSuccessfully() {
            Event event = new Event();
            EventResponse response = mock(EventResponse.class);

            Page<Event> eventPage = new PageImpl<>(
                    List.of(event),
                    PageRequest.of(0, 10),
                    1
            );

            when(eventRepository.findAllByIsDeletedFalse(any(Pageable.class)))
                    .thenReturn(eventPage);
            when(eventMapper.toResponse(event)).thenReturn(response);

            PageResponse<EventResponse> actualResponse = eventService.getAllEvents(0, 10);

            assertThat(actualResponse).isNotNull();

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(eventRepository).findAllByIsDeletedFalse(pageableCaptor.capture());

            Pageable pageable = pageableCaptor.getValue();

            assertThat(pageable.getPageNumber()).isZero();
            assertThat(pageable.getPageSize()).isEqualTo(10);
            assertThat(pageable.getSort())
                    .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));

            verify(eventMapper).toResponse(event);
            verifyNoInteractions(eventValidator);
        }
    }

    @Nested
    @DisplayName("updateEvent")
    class UpdateEventTest {

        @Test
        @DisplayName("Should update event successfully")
        void shouldUpdateEventSuccessfully() {
            EventUpdateRequest request = mock(EventUpdateRequest.class);
            Event event = new Event();
            Event updatedEvent = new Event();
            EventResponse expectedResponse = mock(EventResponse.class);

            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID))
                    .thenReturn(Optional.of(event));
            when(eventRepository.save(event)).thenReturn(updatedEvent);
            when(eventMapper.toResponse(updatedEvent)).thenReturn(expectedResponse);

            EventResponse actualResponse = eventService.updateEvent(EVENT_ID, request);

            assertThat(actualResponse).isSameAs(expectedResponse);

            verify(eventRepository).findByIdAndIsDeletedFalse(EVENT_ID);
            verify(eventValidator).validateBeforeUpdate(event);
            verify(eventMapper).updateEntityFromRequest(request, event);
            verify(eventRepository).save(event);
            verify(eventMapper).toResponse(updatedEvent);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when updating non-existing event")
        void shouldThrowResourceNotFoundExceptionWhenUpdatingNonExistingEvent() {
            EventUpdateRequest request = mock(EventUpdateRequest.class);

            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.updateEvent(EVENT_ID, request))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(eventRepository).findByIdAndIsDeletedFalse(EVENT_ID);
            verifyNoInteractions(eventValidator, eventMapper);
            verify(eventRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should not save event when validator rejects update")
        void shouldNotSaveEventWhenValidatorRejectsUpdate() {
            EventUpdateRequest request = mock(EventUpdateRequest.class);
            Event event = new Event();

            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID))
                    .thenReturn(Optional.of(event));

            doThrow(new BusinessException("Event cannot be updated"))
                    .when(eventValidator).validateBeforeUpdate(event);

            assertThatThrownBy(() -> eventService.updateEvent(EVENT_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Event cannot be updated");

            verify(eventValidator).validateBeforeUpdate(event);
            verify(eventMapper, never()).updateEntityFromRequest(any(), any());
            verify(eventRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateEventStatus")
    class UpdateEventStatusTest {

        @Test
        @DisplayName("Should update event status successfully")
        void shouldUpdateEventStatusSuccessfully() {
            EventStatusUpdateRequest request = mock(EventStatusUpdateRequest.class);
            Event event = new Event();
            Event updatedEvent = new Event();
            EventResponse expectedResponse = mock(EventResponse.class);

            when(request.getStatus()).thenReturn(EventStatus.ACTIVE);
            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID))
                    .thenReturn(Optional.of(event));
            when(eventRepository.save(event)).thenReturn(updatedEvent);
            when(eventMapper.toResponse(updatedEvent)).thenReturn(expectedResponse);

            EventResponse actualResponse = eventService.updateEventStatus(EVENT_ID, request);

            assertThat(actualResponse).isSameAs(expectedResponse);
            assertThat(event.getStatus()).isEqualTo(EventStatus.ACTIVE);

            verify(eventValidator).validateStatusTransition(event, request);
            verify(eventRepository).save(event);
            verify(eventMapper).toResponse(updatedEvent);
        }

        @Test
        @DisplayName("Should not update status when validator rejects transition")
        void shouldNotUpdateStatusWhenValidatorRejectsTransition() {
            EventStatusUpdateRequest request = mock(EventStatusUpdateRequest.class);
            Event event = new Event();
            event.setStatus(EventStatus.INACTIVE);

            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID))
                    .thenReturn(Optional.of(event));

            doThrow(new BusinessException("Invalid status transition"))
                    .when(eventValidator).validateStatusTransition(event, request);

            assertThatThrownBy(() -> eventService.updateEventStatus(EVENT_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Invalid status transition");

            assertThat(event.getStatus()).isEqualTo(EventStatus.INACTIVE);

            verify(eventValidator).validateStatusTransition(event, request);
            verify(eventRepository, never()).save(any());
            verify(eventMapper, never()).toResponse(any());
        }
    }

    @Nested
    @DisplayName("deleteEvent")
    class DeleteEventTest {

        @Test
        @DisplayName("Should soft delete event successfully")
        void shouldSoftDeleteEventSuccessfully() {
            Event event = new Event();
            event.setIsDeleted(false);

            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID))
                    .thenReturn(Optional.of(event));
            when(eventRepository.save(event)).thenReturn(event);

            eventService.deleteEvent(EVENT_ID);

            assertThat(event.getIsDeleted()).isTrue();

            verify(eventValidator).validateBeforeDelete(event);
            verify(eventRepository).save(event);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when deleting non-existing event")
        void shouldThrowResourceNotFoundExceptionWhenDeletingNonExistingEvent() {
            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.deleteEvent(EVENT_ID))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(eventRepository).findByIdAndIsDeletedFalse(EVENT_ID);
            verifyNoInteractions(eventValidator, eventMapper);
            verify(eventRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should not delete event when validator rejects delete")
        void shouldNotDeleteEventWhenValidatorRejectsDelete() {
            Event event = new Event();
            event.setIsDeleted(false);

            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID))
                    .thenReturn(Optional.of(event));

            doThrow(new BusinessException("Active event cannot be deleted"))
                    .when(eventValidator).validateBeforeDelete(event);

            assertThatThrownBy(() -> eventService.deleteEvent(EVENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Active event cannot be deleted");

            assertThat(event.getIsDeleted()).isFalse();

            verify(eventValidator).validateBeforeDelete(event);
            verify(eventRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("activateEvent")
    class ActivateEventTest {

        @Test
        @DisplayName("Should activate event successfully")
        void shouldActivateEventSuccessfully() {
            Event event = new Event();
            event.setStatus(EventStatus.INACTIVE);

            Event updatedEvent = new Event();
            EventResponse expectedResponse = mock(EventResponse.class);

            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID))
                    .thenReturn(Optional.of(event));
            when(eventRepository.save(event)).thenReturn(updatedEvent);
            when(eventMapper.toResponse(updatedEvent)).thenReturn(expectedResponse);

            EventResponse actualResponse = eventService.activateEvent(EVENT_ID);

            assertThat(actualResponse).isSameAs(expectedResponse);
            assertThat(event.getStatus()).isEqualTo(EventStatus.ACTIVE);

            verify(eventValidator).validateBeforeActivate(event);
            verify(eventRepository).save(event);
            verify(eventMapper).toResponse(updatedEvent);
        }

        @Test
        @DisplayName("Should not activate event when validator rejects activation")
        void shouldNotActivateEventWhenValidatorRejectsActivation() {
            Event event = new Event();
            event.setStatus(EventStatus.CANCELLED);

            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID))
                    .thenReturn(Optional.of(event));

            doThrow(new BusinessException("Cancelled event cannot be activated"))
                    .when(eventValidator).validateBeforeActivate(event);

            assertThatThrownBy(() -> eventService.activateEvent(EVENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Cancelled event cannot be activated");

            assertThat(event.getStatus()).isEqualTo(EventStatus.CANCELLED);

            verify(eventValidator).validateBeforeActivate(event);
            verify(eventRepository, never()).save(any());
            verify(eventMapper, never()).toResponse(any());
        }
    }

    @Nested
    @DisplayName("cancelEvent")
    class CancelEventTest {

        @Test
        @DisplayName("Should cancel event successfully")
        void shouldCancelEventSuccessfully() {
            Event event = new Event();
            event.setStatus(EventStatus.ACTIVE);

            Event updatedEvent = new Event();
            EventResponse expectedResponse = mock(EventResponse.class);

            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID))
                    .thenReturn(Optional.of(event));
            when(eventRepository.save(event)).thenReturn(updatedEvent);
            when(eventMapper.toResponse(updatedEvent)).thenReturn(expectedResponse);

            EventResponse actualResponse = eventService.cancelEvent(EVENT_ID);

            assertThat(actualResponse).isSameAs(expectedResponse);
            assertThat(event.getStatus()).isEqualTo(EventStatus.CANCELLED);

            verify(eventValidator).validateBeforeCancel(event);
            verify(eventRepository).save(event);
            verify(eventMapper).toResponse(updatedEvent);
        }

        @Test
        @DisplayName("Should not cancel event when validator rejects cancellation")
        void shouldNotCancelEventWhenValidatorRejectsCancellation() {
            Event event = new Event();
            event.setStatus(EventStatus.ENDED);

            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID))
                    .thenReturn(Optional.of(event));

            doThrow(new BusinessException("Ended event cannot be cancelled"))
                    .when(eventValidator).validateBeforeCancel(event);

            assertThatThrownBy(() -> eventService.cancelEvent(EVENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Ended event cannot be cancelled");

            assertThat(event.getStatus()).isEqualTo(EventStatus.ENDED);

            verify(eventValidator).validateBeforeCancel(event);
            verify(eventRepository, never()).save(any());
            verify(eventMapper, never()).toResponse(any());
        }
    }

    @Nested
    @DisplayName("endEvent")
    class EndEventTest {

        @Test
        @DisplayName("Should end event successfully")
        void shouldEndEventSuccessfully() {
            Event event = new Event();
            event.setStatus(EventStatus.ACTIVE);

            Event updatedEvent = new Event();
            EventResponse expectedResponse = mock(EventResponse.class);

            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID))
                    .thenReturn(Optional.of(event));
            when(eventRepository.save(event)).thenReturn(updatedEvent);
            when(eventMapper.toResponse(updatedEvent)).thenReturn(expectedResponse);

            EventResponse actualResponse = eventService.endEvent(EVENT_ID);

            assertThat(actualResponse).isSameAs(expectedResponse);
            assertThat(event.getStatus()).isEqualTo(EventStatus.ENDED);

            verify(eventValidator).validateBeforeEnd(event);
            verify(eventRepository).save(event);
            verify(eventMapper).toResponse(updatedEvent);
        }

        @Test
        @DisplayName("Should not end event when validator rejects ending")
        void shouldNotEndEventWhenValidatorRejectsEnding() {
            Event event = new Event();
            event.setStatus(EventStatus.CANCELLED);

            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID))
                    .thenReturn(Optional.of(event));

            doThrow(new BusinessException("Cancelled event cannot be ended"))
                    .when(eventValidator).validateBeforeEnd(event);

            assertThatThrownBy(() -> eventService.endEvent(EVENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Cancelled event cannot be ended");

            assertThat(event.getStatus()).isEqualTo(EventStatus.CANCELLED);

            verify(eventValidator).validateBeforeEnd(event);
            verify(eventRepository, never()).save(any());
            verify(eventMapper, never()).toResponse(any());
        }
    }

    @Nested
    @DisplayName("getActiveEvents")
    class GetActiveEventsTest {

        @Test
        @DisplayName("Should get paginated active events sorted by createdAt desc")
        void shouldGetActiveEventsSuccessfully() {
            Event event = new Event();
            EventResponse response = mock(EventResponse.class);

            Page<Event> eventPage = new PageImpl<>(
                    List.of(event),
                    PageRequest.of(0, 10),
                    1
            );

            when(eventRepository.findAllByStatusAndIsDeletedFalse(
                    eq(EventStatus.ACTIVE),
                    any(Pageable.class)
            )).thenReturn(eventPage);

            when(eventMapper.toResponse(event)).thenReturn(response);

            PageResponse<EventResponse> actualResponse = eventService.getActiveEvents(0, 10);

            assertThat(actualResponse).isNotNull();

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

            verify(eventRepository).findAllByStatusAndIsDeletedFalse(
                    eq(EventStatus.ACTIVE),
                    pageableCaptor.capture()
            );

            Pageable pageable = pageableCaptor.getValue();

            assertThat(pageable.getPageNumber()).isZero();
            assertThat(pageable.getPageSize()).isEqualTo(10);
            assertThat(pageable.getSort())
                    .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));

            verify(eventMapper).toResponse(event);
            verifyNoInteractions(eventValidator);
        }
    }
}