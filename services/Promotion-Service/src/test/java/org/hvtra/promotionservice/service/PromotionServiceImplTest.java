//package org.hvtra.promotionservice.service;
//
//import org.hvtra.commonservice.exception.ResourceNotFoundException;
//import org.hvtra.promotionservice.dto.request.promotion.PromotionCreateRequest;
//import org.hvtra.promotionservice.dto.request.promotion.PromotionStatusUpdateRequest;
//import org.hvtra.promotionservice.dto.request.promotion.PromotionUpdateRequest;
//import org.hvtra.promotionservice.dto.response.PageResponse;
//import org.hvtra.promotionservice.dto.response.PromotionResponse;
//import org.hvtra.promotionservice.entity.Event;
//import org.hvtra.promotionservice.entity.Promotion;
//import org.hvtra.promotionservice.enums.promotion.PromotionStatus;
//import org.hvtra.promotionservice.mapper.PromotionMapper;
//import org.hvtra.promotionservice.repository.event.EventRepository;
//import org.hvtra.promotionservice.repository.PromotionRepository;
//import org.hvtra.promotionservice.service.impl.PromotionServiceImpl;
//import org.hvtra.promotionservice.validation.validator.PromotionValidator;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.ArgumentCaptor;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.data.domain.*;
//
//import java.util.List;
//import java.util.Optional;
//import java.util.UUID;
//
//import static org.assertj.core.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//class PromotionServiceImplTest {
//
//    @Mock
//    PromotionRepository promotionRepository;
//
//    @Mock
//    EventRepository eventRepository;
//
//    @Mock
//    PromotionMapper promotionMapper;
//
//    @Mock
//    PromotionValidator promotionValidator;
//
//    @InjectMocks
//    PromotionServiceImpl promotionService;
//
//    UUID eventId;
//    UUID promotionId;
//
//    Event event;
//    Promotion promotion;
//    PromotionResponse promotionResponse;
//
//    @BeforeEach
//    void setUp() {
//        eventId = UUID.randomUUID();
//        promotionId = UUID.randomUUID();
//
//        event = Event.builder()
//                .build();
//        event.setId(eventId);
//        event.setIsDeleted(false);
//
//        promotion = Promotion.builder()
//                .build();
//        promotion.setId(promotionId);
//        promotion.setEvent(event);
//        promotion.setIsDeleted(false);
//
//        promotionResponse = PromotionResponse.builder()
//                .id(promotionId)
//                .build();
//    }
//
//    @Test
//    void createPromotion_success() {
//        PromotionCreateRequest request = PromotionCreateRequest.builder()
//                .eventId(eventId)
//                .build();
//
//        Promotion mappedPromotion = Promotion.builder().build();
//
//        when(eventRepository.findByIdAndIsDeletedFalse(eventId))
//                .thenReturn(Optional.of(event));
//        when(promotionMapper.toEntity(request))
//                .thenReturn(mappedPromotion);
//        when(promotionRepository.save(mappedPromotion))
//                .thenReturn(mappedPromotion);
//        when(promotionMapper.toResponse(mappedPromotion))
//                .thenReturn(promotionResponse);
//
//        PromotionResponse result = promotionService.createPromotion(request);
//
//        assertThat(result).isEqualTo(promotionResponse);
//
//        verify(promotionValidator).validateCreateRequest(request);
//        verify(promotionValidator).validateEventCanCreatePromotion(event);
//        verify(promotionValidator).validatePromotionTimeWithinEvent(request, event);
//
//        assertThat(mappedPromotion.getEvent()).isEqualTo(event);
//        assertThat(mappedPromotion.getStackable()).isFalse();
//        assertThat(mappedPromotion.getPriority()).isZero();
//
//        verify(promotionRepository).save(mappedPromotion);
//    }
//
//    @Test
//    void createPromotionByEvent_success() {
//        PromotionCreateRequest request = PromotionCreateRequest.builder()
//                .build();
//
//        Promotion mappedPromotion = Promotion.builder()
//                .stackable(true)
//                .priority(10)
//                .build();
//
//        when(eventRepository.findByIdAndIsDeletedFalse(eventId))
//                .thenReturn(Optional.of(event));
//        when(promotionMapper.toEntity(request))
//                .thenReturn(mappedPromotion);
//        when(promotionRepository.save(mappedPromotion))
//                .thenReturn(mappedPromotion);
//        when(promotionMapper.toResponse(mappedPromotion))
//                .thenReturn(promotionResponse);
//
//        PromotionResponse result = promotionService.createPromotionByEvent(eventId, request);
//
//        assertThat(result).isEqualTo(promotionResponse);
//        assertThat(mappedPromotion.getEvent()).isEqualTo(event);
//        assertThat(mappedPromotion.getStackable()).isTrue();
//        assertThat(mappedPromotion.getPriority()).isEqualTo(10);
//
//        verify(promotionValidator, never()).validateCreateRequest(any());
//        verify(promotionRepository).save(mappedPromotion);
//    }
//
//    @Test
//    void createPromotion_whenEventNotFound_throwResourceNotFoundException() {
//        PromotionCreateRequest request = PromotionCreateRequest.builder()
//                .eventId(eventId)
//                .build();
//
//        when(eventRepository.findByIdAndIsDeletedFalse(eventId))
//                .thenReturn(Optional.empty());
//
//        assertThatThrownBy(() -> promotionService.createPromotion(request))
//                .isInstanceOf(ResourceNotFoundException.class);
//
//        verify(promotionRepository, never()).save(any());
//    }
//
//    @Test
//    void getPromotionById_success() {
//        when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
//                .thenReturn(Optional.of(promotion));
//        when(promotionMapper.toResponse(promotion))
//                .thenReturn(promotionResponse);
//
//        PromotionResponse result = promotionService.getPromotionById(promotionId);
//
//        assertThat(result).isEqualTo(promotionResponse);
//
//        verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
//        verify(promotionMapper).toResponse(promotion);
//    }
//
//    @Test
//    void getPromotionById_whenNotFound_throwResourceNotFoundException() {
//        when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
//                .thenReturn(Optional.empty());
//
//        assertThatThrownBy(() -> promotionService.getPromotionById(promotionId))
//                .isInstanceOf(ResourceNotFoundException.class);
//
//        verify(promotionMapper, never()).toResponse(any());
//    }
//
//    @Test
//    void getAllPromotions_success() {
//        int page = 0;
//        int size = 10;
//
//        Page<Promotion> promotionPage = new PageImpl<>(
//                List.of(promotion),
//                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")),
//                1
//        );
//
//        when(promotionRepository.findAllByIsDeletedFalse(any(Pageable.class)))
//                .thenReturn(promotionPage);
//        when(promotionMapper.toResponse(promotion))
//                .thenReturn(promotionResponse);
//
//        PageResponse<PromotionResponse> result = promotionService.getAllPromotions(page, size);
//
//        assertThat(result).isNotNull();
//        assertThat(result.getContent()).hasSize(1);
//        assertThat(result.getContent().getFirst()).isEqualTo(promotionResponse);
//
//        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
//        verify(promotionRepository).findAllByIsDeletedFalse(pageableCaptor.capture());
//
//        Pageable pageable = pageableCaptor.getValue();
//
//        assertThat(pageable.getPageNumber()).isEqualTo(page);
//        assertThat(pageable.getPageSize()).isEqualTo(size);
//        assertThat(pageable.getSort())
//                .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
//    }
//
//    @Test
//    void getPromotionsByEvent_success() {
//        int page = 0;
//        int size = 10;
//
//        Page<Promotion> promotionPage = new PageImpl<>(
//                List.of(promotion),
//                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")),
//                1
//        );
//
//        when(eventRepository.findByIdAndIsDeletedFalse(eventId))
//                .thenReturn(Optional.of(event));
//        when(promotionRepository.findAllByEventIdAndIsDeletedFalse(eq(eventId), any(Pageable.class)))
//                .thenReturn(promotionPage);
//        when(promotionMapper.toResponse(promotion))
//                .thenReturn(promotionResponse);
//
//        PageResponse<PromotionResponse> result =
//                promotionService.getPromotionsByEvent(eventId, page, size);
//
//        assertThat(result).isNotNull();
//        assertThat(result.getContent()).hasSize(1);
//        assertThat(result.getContent().getFirst()).isEqualTo(promotionResponse);
//
//        verify(eventRepository).findByIdAndIsDeletedFalse(eventId);
//        verify(promotionRepository).findAllByEventIdAndIsDeletedFalse(eq(eventId), any(Pageable.class));
//    }
//
//    @Test
//    void getPromotionsByEvent_whenEventNotFound_throwResourceNotFoundException() {
//        when(eventRepository.findByIdAndIsDeletedFalse(eventId))
//                .thenReturn(Optional.empty());
//
//        assertThatThrownBy(() -> promotionService.getPromotionsByEvent(eventId, 0, 10))
//                .isInstanceOf(ResourceNotFoundException.class);
//
//        verify(promotionRepository, never())
//                .findAllByEventIdAndIsDeletedFalse(any(), any());
//    }
//
//    @Test
//    void updatePromotion_success() {
//        PromotionUpdateRequest request = PromotionUpdateRequest.builder()
//                .build();
//
//        when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
//                .thenReturn(Optional.of(promotion));
//        when(promotionRepository.save(promotion))
//                .thenReturn(promotion);
//        when(promotionMapper.toResponse(promotion))
//                .thenReturn(promotionResponse);
//
//        PromotionResponse result = promotionService.updatePromotion(promotionId, request);
//
//        assertThat(result).isEqualTo(promotionResponse);
//
//        verify(promotionValidator).validateBeforeUpdate(promotion);
//        verify(promotionMapper).updateEntityFromRequest(request, promotion);
//        verify(promotionRepository).save(promotion);
//    }
//
//    @Test
//    void updatePromotion_whenPromotionNotFound_throwResourceNotFoundException() {
//        PromotionUpdateRequest request = PromotionUpdateRequest.builder()
//                .build();
//
//        when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
//                .thenReturn(Optional.empty());
//
//        assertThatThrownBy(() -> promotionService.updatePromotion(promotionId, request))
//                .isInstanceOf(ResourceNotFoundException.class);
//
//        verify(promotionValidator, never()).validateBeforeUpdate(any());
//        verify(promotionRepository, never()).save(any());
//    }
//
//    @Test
//    void updatePromotionStatus_success() {
//        PromotionStatusUpdateRequest request = PromotionStatusUpdateRequest.builder()
//                .status(PromotionStatus.ACTIVE)
//                .build();
//
//        when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
//                .thenReturn(Optional.of(promotion));
//        when(promotionRepository.save(promotion))
//                .thenReturn(promotion);
//        when(promotionMapper.toResponse(promotion))
//                .thenReturn(promotionResponse);
//
//        PromotionResponse result = promotionService.updatePromotionStatus(promotionId, request);
//
//        assertThat(result).isEqualTo(promotionResponse);
//        assertThat(promotion.getStatus()).isEqualTo(PromotionStatus.ACTIVE);
//
//        verify(promotionValidator).validateStatusTransition(promotion, request);
//        verify(promotionRepository).save(promotion);
//    }
//
//    @Test
//    void deletePromotion_success() {
//        when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
//                .thenReturn(Optional.of(promotion));
//        when(promotionRepository.save(promotion))
//                .thenReturn(promotion);
//
//        promotionService.deletePromotion(promotionId);
//
//        assertThat(promotion.getIsDeleted()).isTrue();
//
//        verify(promotionValidator).validateBeforeDelete(promotion);
//        verify(promotionRepository).save(promotion);
//    }
//
//    @Test
//    void deletePromotion_whenPromotionNotFound_throwResourceNotFoundException() {
//        when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
//                .thenReturn(Optional.empty());
//
//        assertThatThrownBy(() -> promotionService.deletePromotion(promotionId))
//                .isInstanceOf(ResourceNotFoundException.class);
//
//        verify(promotionValidator, never()).validateBeforeDelete(any());
//        verify(promotionRepository, never()).save(any());
//    }
//}