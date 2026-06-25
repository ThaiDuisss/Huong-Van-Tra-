package org.hvtra.promotionservice.service;

import org.hvtra.commonservice.exception.ResourceNotFoundException;
import org.hvtra.promotionservice.dto.request.promotion.PromotionCreateRequest;
import org.hvtra.promotionservice.dto.request.promotion.PromotionStatusUpdateRequest;
import org.hvtra.promotionservice.dto.request.promotion.PromotionUpdateRequest;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.dto.response.PromotionResponse;
import org.hvtra.promotionservice.entity.Promotion;
import org.hvtra.promotionservice.enums.promotion.PromotionApplyMethod;
import org.hvtra.promotionservice.enums.promotion.PromotionStatus;
import org.hvtra.promotionservice.enums.promotion.PromotionType;
import org.hvtra.promotionservice.mapper.PromotionMapper;
import org.hvtra.promotionservice.repository.PromotionRepository;
import org.hvtra.promotionservice.service.impl.PromotionServiceImpl;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromotionServiceImplTest {

    @Mock
    PromotionRepository promotionRepository;

    @Mock
    PromotionMapper promotionMapper;

    @InjectMocks
    PromotionServiceImpl promotionService;

    @Nested
    @DisplayName("createPromotion")
    class CreatePromotionTest {

        @Test
        @DisplayName("should create promotion successfully")
        void shouldCreatePromotionSuccessfully() {
            PromotionCreateRequest request = createPromotionCreateRequest();
            Promotion promotion = createPromotion();
            Promotion savedPromotion = createPromotion();
            PromotionResponse response = createPromotionResponse();

            when(promotionMapper.toEntity(request)).thenReturn(promotion);
            when(promotionRepository.save(promotion)).thenReturn(savedPromotion);
            when(promotionMapper.toResponse(savedPromotion)).thenReturn(response);

            PromotionResponse result = promotionService.createPromotion(request);

            assertThat(result).isEqualTo(response);

            verify(promotionMapper).toEntity(request);
            verify(promotionRepository).save(promotion);
            verify(promotionMapper).toResponse(savedPromotion);
        }
    }

    @Nested
    @DisplayName("getPromotionById")
    class GetPromotionByIdTest {

        @Test
        @DisplayName("should return promotion when promotion exists")
        void shouldReturnPromotionWhenPromotionExists() {
            UUID promotionId = UUID.randomUUID();
            Promotion promotion = createPromotion();
            PromotionResponse response = createPromotionResponse();

            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
                    .thenReturn(Optional.of(promotion));
            when(promotionMapper.toResponse(promotion)).thenReturn(response);

            PromotionResponse result = promotionService.getPromotionById(promotionId);

            assertThat(result).isEqualTo(response);

            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
            verify(promotionMapper).toResponse(promotion);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when promotion does not exist")
        void shouldThrowResourceNotFoundExceptionWhenPromotionDoesNotExist() {
            UUID promotionId = UUID.randomUUID();

            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> promotionService.getPromotionById(promotionId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
            verifyNoInteractions(promotionMapper);
        }
    }

    @Nested
    @DisplayName("getAllPromotions")
    class GetAllPromotionsTest {

        @Test
        @DisplayName("should return paged promotions")
        void shouldReturnPagedPromotions() {
            int page = 0;
            int size = 10;

            Promotion promotion = createPromotion();
            PromotionResponse response = createPromotionResponse();

            Page<Promotion> promotionPage = new PageImpl<>(
                    List.of(promotion),
                    PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")),
                    1
            );

            when(promotionRepository.findAllByIsDeletedFalse(any(Pageable.class)))
                    .thenReturn(promotionPage);
            when(promotionMapper.toResponse(promotion)).thenReturn(response);

            PageResponse<PromotionResponse> result = promotionService.getAllPromotions(page, size);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).containsExactly(response);
            assertThat(result.getPage()).isEqualTo(page);
            assertThat(result.getSize()).isEqualTo(size);
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getTotalPages()).isEqualTo(1);

            verify(promotionRepository).findAllByIsDeletedFalse(argThat(pageable ->
                    pageable.getPageNumber() == page
                            && pageable.getPageSize() == size
                            && pageable.getSort().equals(Sort.by(Sort.Direction.DESC, "createdAt"))
            ));
            verify(promotionMapper).toResponse(promotion);
        }
    }

    @Nested
    @DisplayName("updatePromotion")
    class UpdatePromotionTest {

        @Test
        @DisplayName("should update promotion successfully")
        void shouldUpdatePromotionSuccessfully() {
            UUID promotionId = UUID.randomUUID();
            PromotionUpdateRequest request = createPromotionUpdateRequest();
            Promotion promotion = createPromotion();
            Promotion updatedPromotion = createPromotion();
            PromotionResponse response = createPromotionResponse();

            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
                    .thenReturn(Optional.of(promotion));
            doNothing().when(promotionMapper).updateEntityFromRequest(request, promotion);
            when(promotionRepository.save(promotion)).thenReturn(updatedPromotion);
            when(promotionMapper.toResponse(updatedPromotion)).thenReturn(response);

            PromotionResponse result = promotionService.updatePromotion(promotionId, request);

            assertThat(result).isEqualTo(response);

            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
            verify(promotionMapper).updateEntityFromRequest(request, promotion);
            verify(promotionRepository).save(promotion);
            verify(promotionMapper).toResponse(updatedPromotion);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when promotion does not exist")
        void shouldThrowResourceNotFoundExceptionWhenPromotionDoesNotExist() {
            UUID promotionId = UUID.randomUUID();
            PromotionUpdateRequest request = createPromotionUpdateRequest();

            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> promotionService.updatePromotion(promotionId, request))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
            verifyNoMoreInteractions(promotionRepository);
            verifyNoInteractions(promotionMapper);
        }
    }

    @Nested
    @DisplayName("updatePromotionStatus")
    class UpdatePromotionStatusTest {

        @Test
        @DisplayName("should update promotion status successfully")
        void shouldUpdatePromotionStatusSuccessfully() {
            UUID promotionId = UUID.randomUUID();
            PromotionStatusUpdateRequest request = PromotionStatusUpdateRequest.builder()
                    .status(PromotionStatus.INACTIVE)
                    .build();

            Promotion promotion = createPromotion();
            Promotion updatedPromotion = createPromotion();
            PromotionResponse response = createPromotionResponse();

            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
                    .thenReturn(Optional.of(promotion));
            when(promotionRepository.save(promotion)).thenReturn(updatedPromotion);
            when(promotionMapper.toResponse(updatedPromotion)).thenReturn(response);

            PromotionResponse result = promotionService.updatePromotionStatus(promotionId, request);

            assertThat(result).isEqualTo(response);
            assertThat(promotion.getStatus()).isEqualTo(PromotionStatus.INACTIVE);

            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
            verify(promotionRepository).save(promotion);
            verify(promotionMapper).toResponse(updatedPromotion);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when promotion does not exist")
        void shouldThrowResourceNotFoundExceptionWhenPromotionDoesNotExist() {
            UUID promotionId = UUID.randomUUID();
            PromotionStatusUpdateRequest request = PromotionStatusUpdateRequest.builder()
                    .status(PromotionStatus.INACTIVE)
                    .build();

            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> promotionService.updatePromotionStatus(promotionId, request))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
            verifyNoMoreInteractions(promotionRepository);
            verifyNoInteractions(promotionMapper);
        }
    }

    @Nested
    @DisplayName("deletePromotion")
    class DeletePromotionTest {

        @Test
        @DisplayName("should soft delete promotion successfully")
        void shouldSoftDeletePromotionSuccessfully() {
            UUID promotionId = UUID.randomUUID();
            Promotion promotion = createPromotion();

            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
                    .thenReturn(Optional.of(promotion));
            when(promotionRepository.save(promotion)).thenReturn(promotion);

            promotionService.deletePromotion(promotionId);

            assertThat(promotion.getIsDeleted()).isTrue();

            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
            verify(promotionRepository).save(promotion);
            verifyNoInteractions(promotionMapper);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when promotion does not exist")
        void shouldThrowResourceNotFoundExceptionWhenPromotionDoesNotExist() {
            UUID promotionId = UUID.randomUUID();

            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> promotionService.deletePromotion(promotionId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
            verifyNoMoreInteractions(promotionRepository);
            verifyNoInteractions(promotionMapper);
        }
    }

    private PromotionCreateRequest createPromotionCreateRequest() {
        return PromotionCreateRequest.builder()
                .name("Giảm 20% cho đơn hàng từ 500K")
                .type(PromotionType.PERCENT_DISCOUNT)
                .applyMethod(PromotionApplyMethod.COUPON_CODE)
                .startAt(LocalDateTime.now().plusDays(1))
                .endAt(LocalDateTime.now().plusDays(10))
                .stackable(false)
                .priority(0)
                .build();
    }

    private PromotionUpdateRequest createPromotionUpdateRequest() {
        return PromotionUpdateRequest.builder()
                .name("Giảm 30% cho đơn hàng từ 700K")
                .type(PromotionType.PERCENT_DISCOUNT)
                .applyMethod(PromotionApplyMethod.COUPON_CODE)
                .startAt(LocalDateTime.now().plusDays(2))
                .endAt(LocalDateTime.now().plusDays(12))
                .stackable(false)
                .priority(1)
                .build();
    }

    private Promotion createPromotion() {
        Promotion promotion = Promotion.builder()
                .name("Giảm 20% cho đơn hàng từ 500K")
                .type(PromotionType.PERCENT_DISCOUNT)
                .applyMethod(PromotionApplyMethod.COUPON_CODE)
                .startAt(LocalDateTime.now().plusDays(1))
                .endAt(LocalDateTime.now().plusDays(10))
                .stackable(false)
                .priority(0)
                .status(PromotionStatus.ACTIVE)
                .build();

        promotion.setId(UUID.randomUUID());
        promotion.setIsDeleted(false);

        return promotion;
    }

    private PromotionResponse createPromotionResponse() {
        return PromotionResponse.builder()
                .id(UUID.randomUUID())
                .name("Giảm 20% cho đơn hàng từ 500K")
                .type(PromotionType.PERCENT_DISCOUNT)
                .applyMethod(PromotionApplyMethod.COUPON_CODE)
                .startAt(LocalDateTime.now().plusDays(1))
                .endAt(LocalDateTime.now().plusDays(10))
                .stackable(false)
                .priority(0)
                .status(PromotionStatus.ACTIVE)
                .build();
    }
}
