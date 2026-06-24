package org.hvtra.promotionservice.service;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceImplTest {

    @Mock
    EventRepository eventRepository;

    @Mock
    EventMapper eventMapper;

    @InjectMocks
    EventServiceImpl eventService;

    @Nested
    @DisplayName("createEvent")
    class CreateEventTest {

        @Test
        @DisplayName("Should set status INACTIVE and save event successfully")
        void createEvent_success() {
            // Arrange
            LocalDateTime startAt = LocalDateTime.now().plusDays(1);
            LocalDateTime endAt = LocalDateTime.now().plusDays(10);

            EventCreateRequest request = EventCreateRequest.builder()
                    .name("Tet Holiday Campaign 2027")
                    .description("Chương trình khuyến mãi Tết")
                    .startAt(startAt)
                    .endAt(endAt)
                    .build();

            Event mappedEvent = Event.builder()
                    .name(request.getName())
                    .description(request.getDescription())
                    .startAt(request.getStartAt())
                    .endAt(request.getEndAt())
                    .build();

            Event savedEvent = Event.builder()
                    .name(request.getName())
                    .description(request.getDescription())
                    .startAt(request.getStartAt())
                    .endAt(request.getEndAt())
                    .status(EventStatus.INACTIVE)
                    .build();

            UUID responseId = UUID.randomUUID();

            EventResponse expectedResponse = EventResponse.builder()
                    .id(responseId)
                    .name(savedEvent.getName())
                    .description(savedEvent.getDescription())
                    .startAt(savedEvent.getStartAt())
                    .endAt(savedEvent.getEndAt())
                    .status(savedEvent.getStatus())
                    .build();

            when(eventMapper.toEntity(request)).thenReturn(mappedEvent);
            when(eventRepository.save(mappedEvent)).thenReturn(savedEvent);
            when(eventMapper.toResponse(savedEvent)).thenReturn(expectedResponse);

            // Act
            EventResponse actualResponse = eventService.createEvent(request);

            // Assert
            assertThat(mappedEvent.getStatus()).isEqualTo(EventStatus.INACTIVE);
            assertThat(actualResponse).isEqualTo(expectedResponse);

            verify(eventMapper).toEntity(request);
            verify(eventRepository).save(mappedEvent);
            verify(eventMapper).toResponse(savedEvent);
        }
    }

    @Nested
    @DisplayName("getEventById")
    class GetEventByIdTest {

        @Test
        @DisplayName("Should return event response when event exists")
        void getEventById_success() {
            // Arrange
            UUID eventId = UUID.randomUUID();

            LocalDateTime startAt = LocalDateTime.now().plusDays(1);
            LocalDateTime endAt = LocalDateTime.now().plusDays(10);

            Event event = Event.builder()
                    .name("Tet Holiday Campaign 2027")
                    .description("Chương trình khuyến mãi Tết")
                    .startAt(startAt)
                    .endAt(endAt)
                    .status(EventStatus.INACTIVE)
                    .build();

            EventResponse expectedResponse = EventResponse.builder()
                    .id(eventId)
                    .name(event.getName())
                    .description(event.getDescription())
                    .startAt(event.getStartAt())
                    .endAt(event.getEndAt())
                    .status(event.getStatus())
                    .build();

            when(eventRepository.findByIdAndIsDeletedFalse(eventId))
                    .thenReturn(Optional.of(event));

            when(eventMapper.toResponse(event))
                    .thenReturn(expectedResponse);

            // Act
            EventResponse actualResponse = eventService.getEventById(eventId);

            // Assert
            assertThat(actualResponse).isEqualTo(expectedResponse);

            verify(eventRepository).findByIdAndIsDeletedFalse(eventId);
            verify(eventMapper).toResponse(event);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when event does not exist")
        void getEventById_notFound() {
            // Arrange
            UUID eventId = UUID.randomUUID();

            when(eventRepository.findByIdAndIsDeletedFalse(eventId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> eventService.getEventById(eventId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Event not found");

            verify(eventRepository).findByIdAndIsDeletedFalse(eventId);
            verify(eventMapper, never()).toResponse(any(Event.class));
        }
    }

    @Nested
    @DisplayName("getAllEvents")
    class GetAllEventsTest {

        @Test
        @DisplayName("Should return paged event responses when events exist")
        void getAllEvents_success() {
            // Arrange
            int page = 0;
            int size = 10;

            LocalDateTime startAt = LocalDateTime.now().plusDays(1);
            LocalDateTime endAt = LocalDateTime.now().plusDays(10);

            Event event = Event.builder()
                    .name("Tet Holiday Campaign 2027")
                    .description("Chương trình khuyến mãi Tết")
                    .startAt(startAt)
                    .endAt(endAt)
                    .status(EventStatus.INACTIVE)
                    .build();

            EventResponse eventResponse = EventResponse.builder()
                    .id(UUID.randomUUID())
                    .name(event.getName())
                    .description(event.getDescription())
                    .startAt(event.getStartAt())
                    .endAt(event.getEndAt())
                    .status(event.getStatus())
                    .build();

            Pageable pageable = PageRequest.of(
                    page,
                    size,
                    Sort.by(Sort.Direction.DESC, "createdAt")
            );

            Page<Event> eventPage = new PageImpl<>(
                    List.of(event),
                    pageable,
                    1
            );

            when(eventRepository.findAllByIsDeletedFalse(pageable))
                    .thenReturn(eventPage);

            when(eventMapper.toResponse(event))
                    .thenReturn(eventResponse);

            // Act
            PageResponse<EventResponse> actualResponse = eventService.getAllEvents(page, size);

            // Assert
            assertThat(actualResponse).isNotNull();

            assertThat(actualResponse.getContent()).hasSize(1);
            assertThat(actualResponse.getContent().get(0)).isEqualTo(eventResponse);

            assertThat(actualResponse.getPage()).isZero();
            assertThat(actualResponse.getSize()).isEqualTo(10);
            assertThat(actualResponse.getTotalElements()).isEqualTo(1);
            assertThat(actualResponse.getTotalPages()).isEqualTo(1);

            assertThat(actualResponse.isFirst()).isTrue();
            assertThat(actualResponse.isLast()).isTrue();
            assertThat(actualResponse.isEmpty()).isFalse();

            verify(eventRepository).findAllByIsDeletedFalse(pageable);
            verify(eventMapper).toResponse(event);
        }

        @Test
        @DisplayName("Should return empty page when no events exist")
        void getAllEvents_empty() {
            // Arrange
            int page = 0;
            int size = 10;

            Pageable pageable = PageRequest.of(
                    page,
                    size,
                    Sort.by(Sort.Direction.DESC, "createdAt")
            );

            Page<Event> emptyPage = Page.empty(pageable);

            when(eventRepository.findAllByIsDeletedFalse(pageable))
                    .thenReturn(emptyPage);

            // Act
            PageResponse<EventResponse> actualResponse = eventService.getAllEvents(page, size);

            // Assert
            assertThat(actualResponse).isNotNull();

            assertThat(actualResponse.getContent()).isEmpty();

            assertThat(actualResponse.getPage()).isZero();
            assertThat(actualResponse.getSize()).isEqualTo(10);
            assertThat(actualResponse.getTotalElements()).isZero();
            assertThat(actualResponse.getTotalPages()).isZero();

            assertThat(actualResponse.isFirst()).isTrue();
            assertThat(actualResponse.isLast()).isTrue();
            assertThat(actualResponse.isEmpty()).isTrue();

            verify(eventRepository).findAllByIsDeletedFalse(pageable);
            verify(eventMapper, never()).toResponse(any(Event.class));
        }
    }

    @Nested
    @DisplayName("updateEvent")
    class UpdateEventTest {

        @Test
        @DisplayName("Should update event successfully when event exists")
        void updateEvent_success() {
            // Arrange
            UUID eventId = UUID.randomUUID();

            LocalDateTime oldStartAt = LocalDateTime.now().plusDays(1);
            LocalDateTime oldEndAt = LocalDateTime.now().plusDays(10);

            LocalDateTime newStartAt = LocalDateTime.now().plusDays(2);
            LocalDateTime newEndAt = LocalDateTime.now().plusDays(15);

            EventUpdateRequest request = EventUpdateRequest.builder()
                    .name("Updated Tet Holiday Campaign 2027")
                    .description("Updated chương trình khuyến mãi Tết")
                    .startAt(newStartAt)
                    .endAt(newEndAt)
                    .build();

            Event existingEvent = Event.builder()
                    .name("Tet Holiday Campaign 2027")
                    .description("Chương trình khuyến mãi Tết")
                    .startAt(oldStartAt)
                    .endAt(oldEndAt)
                    .status(EventStatus.INACTIVE)
                    .build();

            Event updatedEvent = Event.builder()
                    .name(request.getName())
                    .description(request.getDescription())
                    .startAt(request.getStartAt())
                    .endAt(request.getEndAt())
                    .status(EventStatus.INACTIVE)
                    .build();

            EventResponse expectedResponse = EventResponse.builder()
                    .id(eventId)
                    .name(updatedEvent.getName())
                    .description(updatedEvent.getDescription())
                    .startAt(updatedEvent.getStartAt())
                    .endAt(updatedEvent.getEndAt())
                    .status(updatedEvent.getStatus())
                    .build();

            when(eventRepository.findByIdAndIsDeletedFalse(eventId))
                    .thenReturn(Optional.of(existingEvent));

            when(eventRepository.save(existingEvent))
                    .thenReturn(updatedEvent);

            when(eventMapper.toResponse(updatedEvent))
                    .thenReturn(expectedResponse);

            // Act
            EventResponse actualResponse = eventService.updateEvent(eventId, request);

            // Assert
            assertThat(actualResponse).isEqualTo(expectedResponse);

            verify(eventRepository).findByIdAndIsDeletedFalse(eventId);
            verify(eventMapper).updateEntityFromRequest(request, existingEvent);
            verify(eventRepository).save(existingEvent);
            verify(eventMapper).toResponse(updatedEvent);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when updating non-existing event")
        void updateEvent_notFound() {
            // Arrange
            UUID eventId = UUID.randomUUID();

            EventUpdateRequest request = EventUpdateRequest.builder()
                    .name("Updated Tet Holiday Campaign 2027")
                    .description("Updated chương trình khuyến mãi Tết")
                    .startAt(LocalDateTime.now().plusDays(2))
                    .endAt(LocalDateTime.now().plusDays(15))
                    .build();

            when(eventRepository.findByIdAndIsDeletedFalse(eventId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> eventService.updateEvent(eventId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Event not found");

            verify(eventRepository).findByIdAndIsDeletedFalse(eventId);
            verify(eventMapper, never()).updateEntityFromRequest(any(EventUpdateRequest.class), any(Event.class));
            verify(eventRepository, never()).save(any(Event.class));
            verify(eventMapper, never()).toResponse(any(Event.class));
        }
    }

    @Nested
    @DisplayName("updateEventStatus")
    class UpdateEventStatusTest {

        @Test
        @DisplayName("Should update event status successfully when event exists")
        void updateEventStatus_success() {
            // Arrange
            UUID eventId = UUID.randomUUID();

            EventStatusUpdateRequest request = EventStatusUpdateRequest.builder()
                    .status(EventStatus.ACTIVE)
                    .build();

            Event existingEvent = Event.builder()
                    .name("Tet Holiday Campaign 2027")
                    .description("Chương trình khuyến mãi Tết")
                    .startAt(LocalDateTime.now().plusDays(1))
                    .endAt(LocalDateTime.now().plusDays(10))
                    .status(EventStatus.INACTIVE)
                    .build();

            Event savedEvent = Event.builder()
                    .name(existingEvent.getName())
                    .description(existingEvent.getDescription())
                    .startAt(existingEvent.getStartAt())
                    .endAt(existingEvent.getEndAt())
                    .status(EventStatus.ACTIVE)
                    .build();

            EventResponse expectedResponse = EventResponse.builder()
                    .id(eventId)
                    .name(savedEvent.getName())
                    .description(savedEvent.getDescription())
                    .startAt(savedEvent.getStartAt())
                    .endAt(savedEvent.getEndAt())
                    .status(savedEvent.getStatus())
                    .build();

            when(eventRepository.findByIdAndIsDeletedFalse(eventId))
                    .thenReturn(Optional.of(existingEvent));

            when(eventRepository.save(existingEvent))
                    .thenReturn(savedEvent);

            when(eventMapper.toResponse(savedEvent))
                    .thenReturn(expectedResponse);

            // Act
            EventResponse actualResponse = eventService.updateEventStatus(eventId, request);

            // Assert
            assertThat(existingEvent.getStatus()).isEqualTo(EventStatus.ACTIVE);
            assertThat(actualResponse).isEqualTo(expectedResponse);

            verify(eventRepository).findByIdAndIsDeletedFalse(eventId);
            verify(eventRepository).save(existingEvent);
            verify(eventMapper).toResponse(savedEvent);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when updating status of non-existing event")
        void updateEventStatus_notFound() {
            // Arrange
            UUID eventId = UUID.randomUUID();

            EventStatusUpdateRequest request = EventStatusUpdateRequest.builder()
                    .status(EventStatus.ACTIVE)
                    .build();

            when(eventRepository.findByIdAndIsDeletedFalse(eventId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> eventService.updateEventStatus(eventId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Event not found");

            verify(eventRepository).findByIdAndIsDeletedFalse(eventId);
            verify(eventRepository, never()).save(any(Event.class));
            verify(eventMapper, never()).toResponse(any(Event.class));
        }
    }

    @Nested
    @DisplayName("deleteEvent")
    class DeleteEventTest {

        @Test
        @DisplayName("Should soft delete event successfully when event exists")
        void deleteEvent_success() {
            // Arrange
            UUID eventId = UUID.randomUUID();

            Event existingEvent = Event.builder()
                    .name("Tet Holiday Campaign 2027")
                    .description("Chương trình khuyến mãi Tết")
                    .startAt(LocalDateTime.now().plusDays(1))
                    .endAt(LocalDateTime.now().plusDays(10))
                    .status(EventStatus.INACTIVE)
                    .build();

            existingEvent.setIsDeleted(false);

            when(eventRepository.findByIdAndIsDeletedFalse(eventId))
                    .thenReturn(Optional.of(existingEvent));

            // Act
            eventService.deleteEvent(eventId);

            // Assert
            assertThat(existingEvent.getIsDeleted()).isTrue();

            verify(eventRepository).findByIdAndIsDeletedFalse(eventId);
            verify(eventRepository).save(existingEvent);
            verifyNoInteractions(eventMapper);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when deleting non-existing event")
        void deleteEvent_notFound() {
            // Arrange
            UUID eventId = UUID.randomUUID();

            when(eventRepository.findByIdAndIsDeletedFalse(eventId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> eventService.deleteEvent(eventId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Event not found");

            verify(eventRepository).findByIdAndIsDeletedFalse(eventId);
            verify(eventRepository, never()).save(any(Event.class));
            verifyNoInteractions(eventMapper);
        }
    }
}