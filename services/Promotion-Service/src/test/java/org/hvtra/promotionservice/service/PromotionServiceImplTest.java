package org.hvtra.promotionservice.service;

import org.hvtra.commonservice.exception.BusinessException;
import org.hvtra.commonservice.exception.ResourceNotFoundException;
import org.hvtra.promotionservice.dto.request.promotion.PromotionCreateRequest;
import org.hvtra.promotionservice.dto.request.promotion.PromotionFilterRequest;
import org.hvtra.promotionservice.dto.request.promotion.PromotionUpdateRequest;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.dto.response.PromotionResponse;
import org.hvtra.promotionservice.entity.Event;
import org.hvtra.promotionservice.entity.Promotion;
import org.hvtra.promotionservice.enums.event.EventAudience;
import org.hvtra.promotionservice.enums.event.EventStatus;
import org.hvtra.promotionservice.enums.event.EventType;
import org.hvtra.promotionservice.enums.promotion.PromotionApplyMethod;
import org.hvtra.promotionservice.enums.promotion.PromotionStatus;
import org.hvtra.promotionservice.enums.promotion.PromotionType;
import org.hvtra.promotionservice.mapper.PromotionMapper;
import org.hvtra.promotionservice.repository.event.EventRepository;
import org.hvtra.promotionservice.repository.promotion.PromotionRepository;
import org.hvtra.promotionservice.service.impl.PromotionServiceImpl;
import org.hvtra.promotionservice.validation.validator.PromotionValidator;
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
@DisplayName("PromotionServiceImpl")
class PromotionServiceImplTest {

    private static final UUID EVENT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID PROMOTION_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440000");
    private static final LocalDateTime NOW = LocalDateTime.now();
    private static final LocalDateTime PAST_START = NOW.minusDays(1);
    private static final LocalDateTime EVENT_START = NOW.minusDays(3);
    private static final LocalDateTime FUTURE_START = NOW.plusDays(1);
    private static final LocalDateTime FUTURE_END = NOW.plusDays(10);
    private static final LocalDateTime EVENT_END = NOW.plusDays(20);

    @Mock
    PromotionRepository promotionRepository;

    @Mock
    EventRepository eventRepository;

    @Mock
    PromotionMapper promotionMapper;

    PromotionService promotionService;

    @BeforeEach
    void setUp() {
        promotionService = new PromotionServiceImpl(
                promotionRepository,
                eventRepository,
                promotionMapper,
                new PromotionValidator()
        );
    }

    @Nested
    @DisplayName("createPromotion")
    class CreatePromotion {

        @Test
        @DisplayName("creates DRAFT promotion and applies default stackable and priority")
        void createPromotion_setsDefaults() {
            Event event = event(EventStatus.ACTIVE, EVENT_START, EVENT_END);
            PromotionCreateRequest request = createRequest(EVENT_ID, PAST_START, FUTURE_END);
            Promotion mappedPromotion = promotion(null, PAST_START, FUTURE_END, event);
            mappedPromotion.setStackable(null);
            mappedPromotion.setPriority(null);
            Promotion savedPromotion = promotion(PromotionStatus.DRAFT, PAST_START, FUTURE_END, event);
            PromotionResponse response = response(PromotionStatus.DRAFT);

            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));
            when(promotionMapper.toEntity(request)).thenReturn(mappedPromotion);
            when(promotionRepository.save(mappedPromotion)).thenReturn(savedPromotion);
            when(promotionMapper.toResponse(savedPromotion)).thenReturn(response);

            PromotionResponse actual = promotionService.createPromotion(request);

            assertThat(actual).isSameAs(response);
            assertThat(mappedPromotion.getEvent()).isSameAs(event);
            assertThat(mappedPromotion.getStatus()).isEqualTo(PromotionStatus.DRAFT);
            assertThat(mappedPromotion.getStackable()).isFalse();
            assertThat(mappedPromotion.getPriority()).isZero();
            verify(promotionRepository).save(mappedPromotion);
        }

        @Test
        @DisplayName("requires eventId when using generic create endpoint")
        void createPromotion_rejectsMissingEventId() {
            PromotionCreateRequest request = createRequest(null, PAST_START, FUTURE_END);

            assertThatThrownBy(() -> promotionService.createPromotion(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Event ID is required");

            verify(eventRepository, never()).findByIdAndIsDeletedFalse(any());
            verify(promotionRepository, never()).save(any());
        }

        @Test
        @DisplayName("creates promotion by event path without requiring eventId in request body")
        void createPromotionByEvent_usesPathEventId() {
            Event event = event(EventStatus.SCHEDULED, EVENT_START, EVENT_END);
            PromotionCreateRequest request = createRequest(null, FUTURE_START, FUTURE_END);
            Promotion mappedPromotion = promotion(PromotionStatus.DRAFT, FUTURE_START, FUTURE_END, event);
            mappedPromotion.setStackable(true);
            mappedPromotion.setPriority(9);
            PromotionResponse response = response(PromotionStatus.DRAFT);

            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));
            when(promotionMapper.toEntity(request)).thenReturn(mappedPromotion);
            when(promotionRepository.save(mappedPromotion)).thenReturn(mappedPromotion);
            when(promotionMapper.toResponse(mappedPromotion)).thenReturn(response);

            PromotionResponse actual = promotionService.createPromotionByEvent(EVENT_ID, request);

            assertThat(actual).isSameAs(response);
            assertThat(mappedPromotion.getEvent()).isSameAs(event);
            assertThat(mappedPromotion.getStackable()).isTrue();
            assertThat(mappedPromotion.getPriority()).isEqualTo(9);
        }

        @Test
        @DisplayName("rejects create under ended event")
        void createPromotion_rejectsEndedEvent() {
            Event event = event(EventStatus.ENDED, EVENT_START, EVENT_END);
            PromotionCreateRequest request = createRequest(EVENT_ID, PAST_START, FUTURE_END);

            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));

            assertThatThrownBy(() -> promotionService.createPromotion(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("ended event");

            verify(promotionRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejects promotion time outside event range")
        void createPromotion_rejectsTimeOutsideEvent() {
            Event event = event(EventStatus.ACTIVE, EVENT_START, EVENT_END);
            PromotionCreateRequest request = createRequest(EVENT_ID, EVENT_START.minusHours(1), FUTURE_END);

            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));

            assertThatThrownBy(() -> promotionService.createPromotion(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("before event start time");

            verify(promotionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("read and list")
    class ReadAndList {

        @Test
        @DisplayName("returns promotion by id")
        void getPromotionById_returnsExistingPromotion() {
            Promotion promotion = promotion(PromotionStatus.DRAFT, FUTURE_START, FUTURE_END, activeEvent());
            PromotionResponse response = response(PromotionStatus.DRAFT);

            when(promotionRepository.findByIdAndIsDeletedFalse(PROMOTION_ID)).thenReturn(Optional.of(promotion));
            when(promotionMapper.toResponse(promotion)).thenReturn(response);

            PromotionResponse actual = promotionService.getPromotionById(PROMOTION_ID);

            assertThat(actual).isSameAs(response);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when promotion is missing or deleted")
        void getPromotionById_throwsWhenMissing() {
            when(promotionRepository.findByIdAndIsDeletedFalse(PROMOTION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> promotionService.getPromotionById(PROMOTION_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(PROMOTION_ID.toString());
        }

        @Test
        @DisplayName("searches promotions with all supported filters")
        void getPromotions_searchesAndMapsPage() {
            PromotionFilterRequest filter = PromotionFilterRequest.builder()
                    .keyword("tet")
                    .eventId(EVENT_ID)
                    .type(PromotionType.PERCENT_DISCOUNT)
                    .applyMethod(PromotionApplyMethod.AUTO)
                    .status(PromotionStatus.ACTIVE)
                    .startFrom(EVENT_START)
                    .startTo(FUTURE_START)
                    .endFrom(FUTURE_START)
                    .endTo(EVENT_END)
                    .build();
            Promotion promotion = promotion(PromotionStatus.ACTIVE, PAST_START, FUTURE_END, activeEvent());
            PromotionResponse response = response(PromotionStatus.ACTIVE);

            when(promotionRepository.searchPromotions(eq(filter), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(promotion)));
            when(promotionMapper.toResponse(promotion)).thenReturn(response);

            PageResponse<PromotionResponse> actual = promotionService.getPromotions(filter, 1, 20);

            assertThat(actual.getContent()).containsExactly(response);
            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(promotionRepository).searchPromotions(eq(filter), pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
            assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
        }

        @Test
        @DisplayName("accepts null filter")
        void getPromotions_acceptsNullFilter() {
            Promotion promotion = promotion(PromotionStatus.DRAFT, FUTURE_START, FUTURE_END, activeEvent());
            PromotionResponse response = response(PromotionStatus.DRAFT);

            when(promotionRepository.searchPromotions(any(PromotionFilterRequest.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(promotion)));
            when(promotionMapper.toResponse(promotion)).thenReturn(response);

            PageResponse<PromotionResponse> actual = promotionService.getPromotions(null, 0, 10);

            assertThat(actual.getContent()).containsExactly(response);
        }

        @Test
        @DisplayName("rejects invalid start range")
        void getPromotions_rejectsInvalidStartRange() {
            PromotionFilterRequest filter = PromotionFilterRequest.builder()
                    .startFrom(FUTURE_START)
                    .startTo(PAST_START)
                    .build();

            assertThatThrownBy(() -> promotionService.getPromotions(filter, 0, 10))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Start from time");

            verify(promotionRepository, never()).searchPromotions(any(), any());
        }

        @Test
        @DisplayName("sets eventId from path when listing promotions by event")
        void getPromotionsByEvent_setsEventIdOnFilter() {
            Event event = activeEvent();
            PromotionFilterRequest filter = PromotionFilterRequest.builder()
                    .keyword("vip")
                    .build();
            Promotion promotion = promotion(PromotionStatus.ACTIVE, PAST_START, FUTURE_END, event);
            PromotionResponse response = response(PromotionStatus.ACTIVE);

            when(eventRepository.findByIdAndIsDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));
            when(promotionRepository.searchPromotions(eq(filter), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(promotion)));
            when(promotionMapper.toResponse(promotion)).thenReturn(response);

            PageResponse<PromotionResponse> actual = promotionService.getPromotionsByEvent(EVENT_ID, filter, 0, 10);

            assertThat(actual.getContent()).containsExactly(response);
            assertThat(filter.getEventId()).isEqualTo(EVENT_ID);
        }
    }

    @Nested
    @DisplayName("update and delete")
    class UpdateAndDelete {

        @Test
        @DisplayName("updates editable DRAFT promotion")
        void updatePromotion_updatesDraftPromotion() {
            Promotion promotion = promotion(PromotionStatus.DRAFT, FUTURE_START, FUTURE_END, activeEvent());
            PromotionUpdateRequest request = updateRequest(FUTURE_START, FUTURE_END);
            PromotionResponse response = response(PromotionStatus.DRAFT);

            when(promotionRepository.findByIdAndIsDeletedFalse(PROMOTION_ID)).thenReturn(Optional.of(promotion));
            when(promotionRepository.save(promotion)).thenReturn(promotion);
            when(promotionMapper.toResponse(promotion)).thenReturn(response);

            PromotionResponse actual = promotionService.updatePromotion(PROMOTION_ID, request);

            assertThat(actual).isSameAs(response);
            verify(promotionMapper).updateEntityFromRequest(request, promotion);
            verify(promotionRepository).save(promotion);
        }

        @Test
        @DisplayName("rejects update for ACTIVE promotion")
        void updatePromotion_rejectsActivePromotion() {
            Promotion promotion = promotion(PromotionStatus.ACTIVE, PAST_START, FUTURE_END, activeEvent());

            when(promotionRepository.findByIdAndIsDeletedFalse(PROMOTION_ID)).thenReturn(Optional.of(promotion));

            assertThatThrownBy(() -> promotionService.updatePromotion(PROMOTION_ID, updateRequest(FUTURE_START, FUTURE_END)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Active promotion cannot be updated");

            verify(promotionRepository, never()).save(any());
        }

        @Test
        @DisplayName("soft deletes DRAFT promotion")
        void deletePromotion_softDeletesDraftPromotion() {
            Promotion promotion = promotion(PromotionStatus.DRAFT, FUTURE_START, FUTURE_END, activeEvent());

            when(promotionRepository.findByIdAndIsDeletedFalse(PROMOTION_ID)).thenReturn(Optional.of(promotion));
            when(promotionRepository.save(promotion)).thenReturn(promotion);

            promotionService.deletePromotion(PROMOTION_ID);

            assertThat(promotion.getIsDeleted()).isTrue();
            verify(promotionRepository).save(promotion);
        }

        @Test
        @DisplayName("rejects delete for PAUSED promotion")
        void deletePromotion_rejectsPausedPromotion() {
            Promotion promotion = promotion(PromotionStatus.PAUSED, PAST_START, FUTURE_END, activeEvent());

            when(promotionRepository.findByIdAndIsDeletedFalse(PROMOTION_ID)).thenReturn(Optional.of(promotion));

            assertThatThrownBy(() -> promotionService.deletePromotion(PROMOTION_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Paused promotion cannot be deleted");

            verify(promotionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("status lifecycle")
    class StatusLifecycle {

        @Test
        @DisplayName("schedules DRAFT promotion with future start and scheduled event")
        void schedulePromotion_schedulesDraftPromotion() {
            assertStatusChange(PromotionStatus.DRAFT, PromotionStatus.SCHEDULED, FUTURE_START, FUTURE_END,
                    event(EventStatus.SCHEDULED, EVENT_START, EVENT_END),
                    () -> promotionService.schedulePromotion(PROMOTION_ID));
        }

        @Test
        @DisplayName("rejects scheduling when start time is not in future")
        void schedulePromotion_rejectsPastStart() {
            Promotion promotion = promotion(PromotionStatus.DRAFT, PAST_START, FUTURE_END, activeEvent());

            when(promotionRepository.findByIdAndIsDeletedFalse(PROMOTION_ID)).thenReturn(Optional.of(promotion));

            assertThatThrownBy(() -> promotionService.schedulePromotion(PROMOTION_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("start time must be in the future");

            verify(promotionRepository, never()).save(any());
        }

        @Test
        @DisplayName("activates SCHEDULED promotion when event is active and start time has passed")
        void activatePromotion_activatesScheduledPromotion() {
            assertStatusChange(PromotionStatus.SCHEDULED, PromotionStatus.ACTIVE, PAST_START, FUTURE_END,
                    activeEvent(),
                    () -> promotionService.activatePromotion(PROMOTION_ID));
        }

        @Test
        @DisplayName("rejects activation before start time")
        void activatePromotion_rejectsFutureStart() {
            Promotion promotion = promotion(PromotionStatus.SCHEDULED, FUTURE_START, FUTURE_END, activeEvent());

            when(promotionRepository.findByIdAndIsDeletedFalse(PROMOTION_ID)).thenReturn(Optional.of(promotion));

            assertThatThrownBy(() -> promotionService.activatePromotion(PROMOTION_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("start time has not arrived");

            verify(promotionRepository, never()).save(any());
        }

        @Test
        @DisplayName("pauses ACTIVE promotion")
        void pausePromotion_pausesActivePromotion() {
            assertStatusChange(PromotionStatus.ACTIVE, PromotionStatus.PAUSED, PAST_START, FUTURE_END,
                    activeEvent(),
                    () -> promotionService.pausePromotion(PROMOTION_ID));
        }

        @Test
        @DisplayName("resumes PAUSED promotion to ACTIVE when start time has passed")
        void resumePromotion_returnsActiveWhenStarted() {
            assertStatusChange(PromotionStatus.PAUSED, PromotionStatus.ACTIVE, PAST_START, FUTURE_END,
                    activeEvent(),
                    () -> promotionService.resumePromotion(PROMOTION_ID));
        }

        @Test
        @DisplayName("resumes PAUSED promotion to SCHEDULED when start time is future")
        void resumePromotion_returnsScheduledWhenFutureStart() {
            assertStatusChange(PromotionStatus.PAUSED, PromotionStatus.SCHEDULED, FUTURE_START, FUTURE_END,
                    event(EventStatus.SCHEDULED, EVENT_START, EVENT_END),
                    () -> promotionService.resumePromotion(PROMOTION_ID));
        }

        @Test
        @DisplayName("cancels ACTIVE promotion")
        void cancelPromotion_cancelsActivePromotion() {
            assertStatusChange(PromotionStatus.ACTIVE, PromotionStatus.CANCELLED, PAST_START, FUTURE_END,
                    activeEvent(),
                    () -> promotionService.cancelPromotion(PROMOTION_ID));
        }

        @Test
        @DisplayName("expires ACTIVE promotion")
        void expirePromotion_expiresActivePromotion() {
            assertStatusChange(PromotionStatus.ACTIVE, PromotionStatus.EXPIRED, PAST_START, FUTURE_END,
                    activeEvent(),
                    () -> promotionService.expirePromotion(PROMOTION_ID));
        }

        @Test
        @DisplayName("ends PAUSED promotion")
        void endPromotion_endsPausedPromotion() {
            assertStatusChange(PromotionStatus.PAUSED, PromotionStatus.ENDED, PAST_START, FUTURE_END,
                    activeEvent(),
                    () -> promotionService.endPromotion(PROMOTION_ID));
        }

        @Test
        @DisplayName("rejects status change from ENDED")
        void changeStatus_rejectsEndedPromotion() {
            Promotion promotion = promotion(PromotionStatus.ENDED, PAST_START, FUTURE_END, activeEvent());

            when(promotionRepository.findByIdAndIsDeletedFalse(PROMOTION_ID)).thenReturn(Optional.of(promotion));

            assertThatThrownBy(() -> promotionService.activatePromotion(PROMOTION_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Ended promotion cannot change status");

            verify(promotionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("duplicatePromotion")
    class DuplicatePromotion {

        @Test
        @DisplayName("duplicates non-cancelled promotion as DRAFT")
        void duplicatePromotion_createsDraftCopy() {
            Promotion source = promotion(PromotionStatus.ACTIVE, PAST_START, FUTURE_END, activeEvent());
            Promotion copy = promotion(PromotionStatus.ACTIVE, PAST_START, FUTURE_END, activeEvent());
            copy.setIsDeleted(true);
            PromotionResponse response = response(PromotionStatus.DRAFT);

            when(promotionRepository.findByIdAndIsDeletedFalse(PROMOTION_ID)).thenReturn(Optional.of(source));
            when(promotionMapper.duplicateFrom(source)).thenReturn(copy);
            when(promotionRepository.save(copy)).thenReturn(copy);
            when(promotionMapper.toResponse(copy)).thenReturn(response);

            PromotionResponse actual = promotionService.duplicatePromotion(PROMOTION_ID);

            assertThat(actual).isSameAs(response);
            assertThat(copy.getStatus()).isEqualTo(PromotionStatus.DRAFT);
            assertThat(copy.getIsDeleted()).isFalse();
            verify(promotionRepository).save(copy);
        }

        @Test
        @DisplayName("rejects duplicate for ENDED promotion")
        void duplicatePromotion_rejectsEndedPromotion() {
            Promotion source = promotion(PromotionStatus.ENDED, PAST_START, FUTURE_END, activeEvent());

            when(promotionRepository.findByIdAndIsDeletedFalse(PROMOTION_ID)).thenReturn(Optional.of(source));

            assertThatThrownBy(() -> promotionService.duplicatePromotion(PROMOTION_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Ended promotion cannot be duplicated");

            verify(promotionRepository, never()).save(any());
        }
    }

    private void assertStatusChange(
            PromotionStatus currentStatus,
            PromotionStatus targetStatus,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Event event,
            StatusAction action
    ) {
        Promotion promotion = promotion(currentStatus, startAt, endAt, event);
        PromotionResponse response = response(targetStatus);

        when(promotionRepository.findByIdAndIsDeletedFalse(PROMOTION_ID)).thenReturn(Optional.of(promotion));
        when(promotionRepository.save(promotion)).thenReturn(promotion);
        when(promotionMapper.toResponse(promotion)).thenReturn(response);

        PromotionResponse actual = action.execute();

        assertThat(actual).isSameAs(response);
        assertThat(promotion.getStatus()).isEqualTo(targetStatus);
        verify(promotionRepository).save(promotion);
    }

    private PromotionCreateRequest createRequest(UUID eventId, LocalDateTime startAt, LocalDateTime endAt) {
        return PromotionCreateRequest.builder()
                .eventId(eventId)
                .name("Tet 20 percent discount")
                .description("Discount for Tet campaign")
                .objective("Increase POS revenue")
                .type(PromotionType.PERCENT_DISCOUNT)
                .applyMethod(PromotionApplyMethod.AUTO)
                .startAt(startAt)
                .endAt(endAt)
                .stackable(false)
                .priority(5)
                .build();
    }

    private PromotionUpdateRequest updateRequest(LocalDateTime startAt, LocalDateTime endAt) {
        return PromotionUpdateRequest.builder()
                .name("Updated promotion")
                .description("Updated description")
                .objective("Increase loyalty")
                .type(PromotionType.FIXED_DISCOUNT)
                .applyMethod(PromotionApplyMethod.COUPON_CODE)
                .startAt(startAt)
                .endAt(endAt)
                .stackable(true)
                .priority(10)
                .build();
    }

    private Event activeEvent() {
        return event(EventStatus.ACTIVE, EVENT_START, EVENT_END);
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

    private Promotion promotion(PromotionStatus status, LocalDateTime startAt, LocalDateTime endAt, Event event) {
        Promotion promotion = Promotion.builder()
                .event(event)
                .name("Tet 20 percent discount")
                .description("Discount for Tet campaign")
                .objective("Increase POS revenue")
                .type(PromotionType.PERCENT_DISCOUNT)
                .applyMethod(PromotionApplyMethod.AUTO)
                .startAt(startAt)
                .endAt(endAt)
                .stackable(false)
                .priority(5)
                .status(status)
                .build();
        promotion.setId(PROMOTION_ID);
        promotion.setIsDeleted(false);
        return promotion;
    }

    private PromotionResponse response(PromotionStatus status) {
        return PromotionResponse.builder()
                .id(PROMOTION_ID)
                .eventId(EVENT_ID)
                .eventName("Tet Holiday Campaign 2027")
                .name("Tet 20 percent discount")
                .description("Discount for Tet campaign")
                .objective("Increase POS revenue")
                .type(PromotionType.PERCENT_DISCOUNT)
                .applyMethod(PromotionApplyMethod.AUTO)
                .startAt(PAST_START)
                .endAt(FUTURE_END)
                .stackable(false)
                .priority(5)
                .status(status)
                .isDeleted(false)
                .build();
    }

    @FunctionalInterface
    private interface StatusAction {
        PromotionResponse execute();
    }
}
