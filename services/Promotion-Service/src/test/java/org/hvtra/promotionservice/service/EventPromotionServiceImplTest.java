package org.hvtra.promotionservice.service;

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
import org.hvtra.promotionservice.service.impl.EventPromotionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventPromotionServiceImplTest {

    @Mock
    EventRepository eventRepository;

    @Mock
    PromotionRepository promotionRepository;

    @Mock
    PromotionMapper promotionMapper;

    @InjectMocks
    EventPromotionServiceImpl eventPromotionService;

    UUID eventId;
    UUID promotionId;
    Event event;
    Promotion promotion;
    PromotionResponse promotionResponse;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
        promotionId = UUID.randomUUID();

        event = new Event();
        event.setId(eventId);

        promotion = new Promotion();
        promotion.setId(promotionId);

        promotionResponse = new PromotionResponse();
        promotionResponse.setId(promotionId);
    }

    @Nested
    @DisplayName("getPromotionsByEvent")
    class GetPromotionsByEvent {

        @Test
        @DisplayName("success - trả về danh sách promotion theo event")
        void success() {
            when(eventRepository.findByIdAndIsDeletedFalse(eventId))
                    .thenReturn(Optional.of(event));

            when(promotionRepository.findByEventAndIsDeletedFalse(
                    eq(event),
                    any(PageRequest.class)
            )).thenReturn(new PageImpl<>(List.of(promotion)));

            when(promotionMapper.toResponse(promotion))
                    .thenReturn(promotionResponse);

            PageResponse<PromotionResponse> result =
                    eventPromotionService.getPromotionsByEvent(eventId, 0, 10);

            assertNotNull(result);
            assertEquals(1, result.getContent().size());
            assertEquals(promotionId, result.getContent().getFirst().getId());

            verify(eventRepository).findByIdAndIsDeletedFalse(eventId);
            verify(promotionRepository).findByEventAndIsDeletedFalse(eq(event), any(PageRequest.class));
            verify(promotionMapper).toResponse(promotion);
        }

        @Test
        @DisplayName("success - event có 0 promotion")
        void success_emptyPage() {
            when(eventRepository.findByIdAndIsDeletedFalse(eventId))
                    .thenReturn(Optional.of(event));

            when(promotionRepository.findByEventAndIsDeletedFalse(
                    eq(event),
                    any(PageRequest.class)
            )).thenReturn(new PageImpl<>(List.of()));

            PageResponse<PromotionResponse> result =
                    eventPromotionService.getPromotionsByEvent(eventId, 0, 10);

            assertNotNull(result);
            assertTrue(result.getContent().isEmpty());

            verify(promotionMapper, never()).toResponse(any());
        }

        @Test
        @DisplayName("fail - event không tồn tại")
        void eventNotFound_throwException() {
            when(eventRepository.findByIdAndIsDeletedFalse(eventId))
                    .thenReturn(Optional.empty());

            assertThrows(
                    ResourceNotFoundException.class,
                    () -> eventPromotionService.getPromotionsByEvent(eventId, 0, 10)
            );

            verify(promotionRepository, never()).findByEventAndIsDeletedFalse(any(), any());
            verify(promotionMapper, never()).toResponse(any());
        }
    }

    @Nested
    @DisplayName("createPromotionInEvent")
    class CreatePromotionInEvent {

        @Test
        @DisplayName("success - tạo promotion trong event")
        void success() {
            PromotionCreateRequest request = new PromotionCreateRequest();

            when(eventRepository.findByIdAndIsDeletedFalse(eventId))
                    .thenReturn(Optional.of(event));

            when(promotionMapper.toEntity(request))
                    .thenReturn(promotion);

            when(promotionRepository.save(promotion))
                    .thenReturn(promotion);

            when(promotionMapper.toResponse(promotion))
                    .thenReturn(promotionResponse);

            PromotionResponse result =
                    eventPromotionService.createPromotionInEvent(eventId, request);

            assertNotNull(result);
            assertEquals(promotionId, result.getId());
            assertEquals(event, promotion.getEvent());

            verify(promotionMapper).toEntity(request);
            verify(promotionRepository).save(promotion);
            verify(promotionMapper).toResponse(promotion);
        }

        @Test
        @DisplayName("fail - event không tồn tại")
        void eventNotFound_throwException() {
            PromotionCreateRequest request = new PromotionCreateRequest();

            when(eventRepository.findByIdAndIsDeletedFalse(eventId))
                    .thenReturn(Optional.empty());

            assertThrows(
                    ResourceNotFoundException.class,
                    () -> eventPromotionService.createPromotionInEvent(eventId, request)
            );

            verify(promotionMapper, never()).toEntity(any());
            verify(promotionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("attachPromotionsToEvent")
    class AttachPromotionsToEvent {

        @Test
        @DisplayName("success - gắn nhiều promotion vào event")
        void success_attachManyPromotions() {
            UUID promotionId2 = UUID.randomUUID();

            Promotion promotion2 = new Promotion();
            promotion2.setId(promotionId2);

            EventPromotionRequest request = new EventPromotionRequest();
            request.setPromotionIds(List.of(promotionId, promotionId2));

            List<Promotion> promotions = List.of(promotion, promotion2);

            when(eventRepository.findByIdAndIsDeletedFalse(eventId))
                    .thenReturn(Optional.of(event));

            when(promotionRepository.findAllById(request.getPromotionIds()))
                    .thenReturn(promotions);

            eventPromotionService.attachPromotionsToEvent(eventId, request);

            assertEquals(event, promotion.getEvent());
            assertEquals(event, promotion2.getEvent());

            verify(promotionRepository).saveAll(promotions);
        }

        @Test
        @DisplayName("success - promotion đang thuộc event khác thì chuyển sang event mới")
        void success_promotionBelongsToOtherEvent_changeEvent() {
            Event oldEvent = new Event();
            oldEvent.setId(UUID.randomUUID());

            promotion.setEvent(oldEvent);

            EventPromotionRequest request = new EventPromotionRequest();
            request.setPromotionIds(List.of(promotionId));

            when(eventRepository.findByIdAndIsDeletedFalse(eventId))
                    .thenReturn(Optional.of(event));

            when(promotionRepository.findAllById(request.getPromotionIds()))
                    .thenReturn(List.of(promotion));

            eventPromotionService.attachPromotionsToEvent(eventId, request);

            assertEquals(event, promotion.getEvent());

            verify(promotionRepository).saveAll(List.of(promotion));
        }

        @Test
        @DisplayName("success - request rỗng thì saveAll list rỗng")
        void success_emptyPromotionIds() {
            EventPromotionRequest request = new EventPromotionRequest();
            request.setPromotionIds(List.of());

            when(eventRepository.findByIdAndIsDeletedFalse(eventId))
                    .thenReturn(Optional.of(event));

            when(promotionRepository.findAllById(request.getPromotionIds()))
                    .thenReturn(List.of());

            eventPromotionService.attachPromotionsToEvent(eventId, request);

            verify(promotionRepository).saveAll(List.of());
        }

        @Test
        @DisplayName("fail - event không tồn tại")
        void eventNotFound_throwException() {
            EventPromotionRequest request = new EventPromotionRequest();
            request.setPromotionIds(List.of(promotionId));

            when(eventRepository.findByIdAndIsDeletedFalse(eventId))
                    .thenReturn(Optional.empty());

            assertThrows(
                    ResourceNotFoundException.class,
                    () -> eventPromotionService.attachPromotionsToEvent(eventId, request)
            );

            verify(promotionRepository, never()).findAllById(any());
            verify(promotionRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("fail - có promotion id không tồn tại")
        void promotionNotFound_throwException() {
            UUID missingPromotionId = UUID.randomUUID();

            EventPromotionRequest request = new EventPromotionRequest();
            request.setPromotionIds(List.of(promotionId, missingPromotionId));

            when(eventRepository.findByIdAndIsDeletedFalse(eventId))
                    .thenReturn(Optional.of(event));

            when(promotionRepository.findAllById(request.getPromotionIds()))
                    .thenReturn(List.of(promotion));

            assertThrows(
                    ResourceNotFoundException.class,
                    () -> eventPromotionService.attachPromotionsToEvent(eventId, request)
            );

            verify(promotionRepository, never()).saveAll(any());
        }
    }

    @Nested
    @DisplayName("detachPromotionsFromEvent")
    class DetachPromotionsFromEvent {

        @Test
        @DisplayName("success - gỡ promotion khỏi event")
        void success_detachPromotion() {
            promotion.setEvent(event);

            EventPromotionRequest request = new EventPromotionRequest();
            request.setPromotionIds(List.of(promotionId));

            when(eventRepository.findByIdAndIsDeletedFalse(eventId))
                    .thenReturn(Optional.of(event));

            when(promotionRepository.findAllById(request.getPromotionIds()))
                    .thenReturn(List.of(promotion));

            eventPromotionService.detachPromotionsFromEvent(eventId, request);

            assertNull(promotion.getEvent());

            verify(promotionRepository).saveAll(List.of(promotion));
        }

        @Test
        @DisplayName("success - gỡ nhiều promotion khỏi event")
        void success_detachManyPromotions() {
            UUID promotionId2 = UUID.randomUUID();

            Promotion promotion2 = new Promotion();
            promotion2.setId(promotionId2);

            promotion.setEvent(event);
            promotion2.setEvent(event);

            EventPromotionRequest request = new EventPromotionRequest();
            request.setPromotionIds(List.of(promotionId, promotionId2));

            List<Promotion> promotions = List.of(promotion, promotion2);

            when(eventRepository.findByIdAndIsDeletedFalse(eventId))
                    .thenReturn(Optional.of(event));

            when(promotionRepository.findAllById(request.getPromotionIds()))
                    .thenReturn(promotions);

            eventPromotionService.detachPromotionsFromEvent(eventId, request);

            assertNull(promotion.getEvent());
            assertNull(promotion2.getEvent());

            verify(promotionRepository).saveAll(promotions);
        }

        @Test
        @DisplayName("success - promotion thuộc event khác thì không gỡ")
        void success_promotionBelongsToOtherEvent_notDetach() {
            Event otherEvent = new Event();
            otherEvent.setId(UUID.randomUUID());

            promotion.setEvent(otherEvent);

            EventPromotionRequest request = new EventPromotionRequest();
            request.setPromotionIds(List.of(promotionId));

            when(eventRepository.findByIdAndIsDeletedFalse(eventId))
                    .thenReturn(Optional.of(event));

            when(promotionRepository.findAllById(request.getPromotionIds()))
                    .thenReturn(List.of(promotion));

            eventPromotionService.detachPromotionsFromEvent(eventId, request);

            assertEquals(otherEvent, promotion.getEvent());

            verify(promotionRepository).saveAll(List.of(promotion));
        }

        @Test
        @DisplayName("success - promotion chưa thuộc event nào thì giữ nguyên null")
        void success_promotionHasNoEvent_keepNull() {
            promotion.setEvent(null);

            EventPromotionRequest request = new EventPromotionRequest();
            request.setPromotionIds(List.of(promotionId));

            when(eventRepository.findByIdAndIsDeletedFalse(eventId))
                    .thenReturn(Optional.of(event));

            when(promotionRepository.findAllById(request.getPromotionIds()))
                    .thenReturn(List.of(promotion));

            eventPromotionService.detachPromotionsFromEvent(eventId, request);

            assertNull(promotion.getEvent());

            verify(promotionRepository).saveAll(List.of(promotion));
        }

        @Test
        @DisplayName("fail - event không tồn tại")
        void eventNotFound_throwException() {
            EventPromotionRequest request = new EventPromotionRequest();
            request.setPromotionIds(List.of(promotionId));

            when(eventRepository.findByIdAndIsDeletedFalse(eventId))
                    .thenReturn(Optional.empty());

            assertThrows(
                    ResourceNotFoundException.class,
                    () -> eventPromotionService.detachPromotionsFromEvent(eventId, request)
            );

            verify(promotionRepository, never()).findAllById(any());
            verify(promotionRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("fail - có promotion id không tồn tại")
        void promotionNotFound_throwException() {
            UUID missingPromotionId = UUID.randomUUID();

            EventPromotionRequest request = new EventPromotionRequest();
            request.setPromotionIds(List.of(promotionId, missingPromotionId));

            when(eventRepository.findByIdAndIsDeletedFalse(eventId))
                    .thenReturn(Optional.of(event));

            when(promotionRepository.findAllById(request.getPromotionIds()))
                    .thenReturn(List.of(promotion));

            assertThrows(
                    ResourceNotFoundException.class,
                    () -> eventPromotionService.detachPromotionsFromEvent(eventId, request)
            );

            verify(promotionRepository, never()).saveAll(any());
        }
    }
}
