package org.hvtra.promotionservice.service;

import org.hvtra.commonservice.exception.ResourceNotFoundException;
import org.hvtra.promotionservice.dto.request.promotionbenefit.PromotionBenefitCreateRequest;
import org.hvtra.promotionservice.dto.request.promotionbenefit.PromotionBenefitUpdateRequest;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.dto.response.PromotionBenefitResponse;
import org.hvtra.promotionservice.entity.Promotion;
import org.hvtra.promotionservice.entity.PromotionBenefit;
import org.hvtra.promotionservice.mapper.PromotionBenefitMapper;
import org.hvtra.promotionservice.repository.PromotionBenefitRepository;
import org.hvtra.promotionservice.repository.PromotionRepository;
import org.hvtra.promotionservice.service.impl.PromotionBenefitServiceImpl;
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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PromotionBenefitServiceImpl")
class PromotionBenefitServiceImplTest {

    @Mock
    PromotionRepository promotionRepository;

    @Mock
    PromotionBenefitRepository promotionBenefitRepository;

    @Mock
    PromotionBenefitMapper promotionBenefitMapper;

    @InjectMocks
    PromotionBenefitServiceImpl promotionBenefitService;

    final UUID promotionId = UUID.randomUUID();
    final UUID benefitId = UUID.randomUUID();

    @Nested
    @DisplayName("createBenefit")
    class CreateBenefitTest {

        @Test
        @DisplayName("Tạo benefit thành công")
        void createBenefit_success() {
            // Given
            PromotionBenefitCreateRequest request = mock(PromotionBenefitCreateRequest.class);

            Promotion promotion = new Promotion();
            promotion.setId(promotionId);

            PromotionBenefit benefit = new PromotionBenefit();
            PromotionBenefit savedBenefit = new PromotionBenefit();

            PromotionBenefitResponse response = mock(PromotionBenefitResponse.class);

            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
                    .thenReturn(Optional.of(promotion));

            when(promotionBenefitMapper.toEntity(request))
                    .thenReturn(benefit);

            when(promotionBenefitRepository.save(benefit))
                    .thenReturn(savedBenefit);

            when(promotionBenefitMapper.toResponse(savedBenefit))
                    .thenReturn(response);

            // When
            PromotionBenefitResponse result =
                    promotionBenefitService.createBenefit(promotionId, request);

            // Then
            assertThat(result).isSameAs(response);
            assertThat(benefit.getPromotion()).isSameAs(promotion);

            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
            verify(promotionBenefitMapper).toEntity(request);
            verify(promotionBenefitRepository).save(benefit);
            verify(promotionBenefitMapper).toResponse(savedBenefit);
        }

        @Test
        @DisplayName("Tạo benefit thất bại khi promotion không tồn tại")
        void createBenefit_promotionNotFound_throwException() {
            // Given
            PromotionBenefitCreateRequest request = mock(PromotionBenefitCreateRequest.class);

            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
                    .thenReturn(Optional.empty());

            // When + Then
            assertThatThrownBy(() ->
                    promotionBenefitService.createBenefit(promotionId, request)
            ).isInstanceOf(ResourceNotFoundException.class);

            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
            verifyNoInteractions(promotionBenefitMapper);
            verifyNoInteractions(promotionBenefitRepository);
        }
    }

    @Nested
    @DisplayName("getBenefitsByPromotion")
    class GetBenefitsByPromotionTest {

        @Test
        @DisplayName("Lấy danh sách benefit theo promotion thành công")
        void getBenefitsByPromotion_success() {
            // Given
            int page = 0;
            int size = 10;

            Promotion promotion = new Promotion();
            promotion.setId(promotionId);

            PromotionBenefit benefit = new PromotionBenefit();
            benefit.setId(benefitId);

            PromotionBenefitResponse response = mock(PromotionBenefitResponse.class);

            Page<PromotionBenefit> benefitPage = new PageImpl<>(
                    List.of(benefit),
                    PageRequest.of(page, size),
                    1
            );

            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
                    .thenReturn(Optional.of(promotion));

            when(promotionBenefitRepository.findAllByPromotionIdAndIsDeletedFalse(
                    eq(promotionId),
                    any(Pageable.class)
            )).thenReturn(benefitPage);

            when(promotionBenefitMapper.toResponse(benefit))
                    .thenReturn(response);

            // When
            PageResponse<PromotionBenefitResponse> result =
                    promotionBenefitService.getBenefitsByPromotion(promotionId, page, size);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().getFirst()).isSameAs(response);

            ArgumentCaptor<Pageable> pageableCaptor =
                    ArgumentCaptor.forClass(Pageable.class);

            verify(promotionBenefitRepository).findAllByPromotionIdAndIsDeletedFalse(
                    eq(promotionId),
                    pageableCaptor.capture()
            );

            Pageable pageable = pageableCaptor.getValue();

            assertThat(pageable.getPageNumber()).isEqualTo(page);
            assertThat(pageable.getPageSize()).isEqualTo(size);
            assertThat(pageable.getSort().getOrderFor("createdAt")).isNotNull();
            assertThat(Objects.requireNonNull(pageable.getSort().getOrderFor("createdAt")).getDirection())
                    .isEqualTo(Sort.Direction.DESC);
        }

        @Test
        @DisplayName("Lấy danh sách thất bại khi promotion không tồn tại")
        void getBenefitsByPromotion_promotionNotFound_throwException() {
            // Given
            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
                    .thenReturn(Optional.empty());

            // When + Then
            assertThatThrownBy(() ->
                    promotionBenefitService.getBenefitsByPromotion(promotionId, 0, 10)
            ).isInstanceOf(ResourceNotFoundException.class);

            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
            verifyNoInteractions(promotionBenefitRepository);
            verifyNoInteractions(promotionBenefitMapper);
        }
    }

    @Nested
    @DisplayName("updateBenefit")
    class UpdateBenefitTest {

        @Test
        @DisplayName("Cập nhật benefit thành công")
        void updateBenefit_success() {
            // Given
            PromotionBenefitUpdateRequest request = mock(PromotionBenefitUpdateRequest.class);

            Promotion promotion = new Promotion();
            promotion.setId(promotionId);

            PromotionBenefit benefit = new PromotionBenefit();
            benefit.setId(benefitId);

            PromotionBenefit updatedBenefit = new PromotionBenefit();
            updatedBenefit.setId(benefitId);

            PromotionBenefitResponse response = mock(PromotionBenefitResponse.class);

            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
                    .thenReturn(Optional.of(promotion));

            when(promotionBenefitRepository.findByIdAndPromotionIdAndIsDeletedFalse(
                    benefitId,
                    promotionId
            )).thenReturn(Optional.of(benefit));

            when(promotionBenefitRepository.save(benefit))
                    .thenReturn(updatedBenefit);

            when(promotionBenefitMapper.toResponse(updatedBenefit))
                    .thenReturn(response);

            // When
            PromotionBenefitResponse result =
                    promotionBenefitService.updateBenefit(promotionId, benefitId, request);

            // Then
            assertThat(result).isSameAs(response);

            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
            verify(promotionBenefitRepository)
                    .findByIdAndPromotionIdAndIsDeletedFalse(benefitId, promotionId);
            verify(promotionBenefitMapper).updateEntityFromRequest(request, benefit);
            verify(promotionBenefitRepository).save(benefit);
            verify(promotionBenefitMapper).toResponse(updatedBenefit);
        }

        @Test
        @DisplayName("Cập nhật thất bại khi promotion không tồn tại")
        void updateBenefit_promotionNotFound_throwException() {
            // Given
            PromotionBenefitUpdateRequest request = mock(PromotionBenefitUpdateRequest.class);

            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
                    .thenReturn(Optional.empty());

            // When + Then
            assertThatThrownBy(() ->
                    promotionBenefitService.updateBenefit(promotionId, benefitId, request)
            ).isInstanceOf(ResourceNotFoundException.class);

            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
            verifyNoInteractions(promotionBenefitRepository);
            verifyNoInteractions(promotionBenefitMapper);
        }

        @Test
        @DisplayName("Cập nhật thất bại khi benefit không tồn tại trong promotion")
        void updateBenefit_benefitNotFound_throwException() {
            // Given
            PromotionBenefitUpdateRequest request = mock(PromotionBenefitUpdateRequest.class);

            Promotion promotion = new Promotion();
            promotion.setId(promotionId);

            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
                    .thenReturn(Optional.of(promotion));

            when(promotionBenefitRepository.findByIdAndPromotionIdAndIsDeletedFalse(
                    benefitId,
                    promotionId
            )).thenReturn(Optional.empty());

            // When + Then
            assertThatThrownBy(() ->
                    promotionBenefitService.updateBenefit(promotionId, benefitId, request)
            ).isInstanceOf(ResourceNotFoundException.class);

            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
            verify(promotionBenefitRepository)
                    .findByIdAndPromotionIdAndIsDeletedFalse(benefitId, promotionId);

            verify(promotionBenefitMapper, never())
                    .updateEntityFromRequest(any(), any());

            verify(promotionBenefitRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteBenefit")
    class DeleteBenefitTest {

        @Test
        @DisplayName("Xóa mềm benefit thành công")
        void deleteBenefit_success() {
            // Given
            Promotion promotion = new Promotion();
            promotion.setId(promotionId);

            PromotionBenefit benefit = new PromotionBenefit();
            benefit.setId(benefitId);
            benefit.setIsDeleted(false);

            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
                    .thenReturn(Optional.of(promotion));

            when(promotionBenefitRepository.findByIdAndPromotionIdAndIsDeletedFalse(
                    benefitId,
                    promotionId
            )).thenReturn(Optional.of(benefit));

            // When
            promotionBenefitService.deleteBenefit(promotionId, benefitId);

            // Then
            assertThat(benefit.getIsDeleted()).isTrue();

            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
            verify(promotionBenefitRepository)
                    .findByIdAndPromotionIdAndIsDeletedFalse(benefitId, promotionId);
            verify(promotionBenefitRepository).save(benefit);
        }

        @Test
        @DisplayName("Xóa thất bại khi promotion không tồn tại")
        void deleteBenefit_promotionNotFound_throwException() {
            // Given
            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
                    .thenReturn(Optional.empty());

            // When + Then
            assertThatThrownBy(() ->
                    promotionBenefitService.deleteBenefit(promotionId, benefitId)
            ).isInstanceOf(ResourceNotFoundException.class);

            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
            verifyNoInteractions(promotionBenefitRepository);
        }

        @Test
        @DisplayName("Xóa thất bại khi benefit không tồn tại trong promotion")
        void deleteBenefit_benefitNotFound_throwException() {
            // Given
            Promotion promotion = new Promotion();
            promotion.setId(promotionId);

            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
                    .thenReturn(Optional.of(promotion));

            when(promotionBenefitRepository.findByIdAndPromotionIdAndIsDeletedFalse(
                    benefitId,
                    promotionId
            )).thenReturn(Optional.empty());

            // When + Then
            assertThatThrownBy(() ->
                    promotionBenefitService.deleteBenefit(promotionId, benefitId)
            ).isInstanceOf(ResourceNotFoundException.class);

            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
            verify(promotionBenefitRepository)
                    .findByIdAndPromotionIdAndIsDeletedFalse(benefitId, promotionId);
            verify(promotionBenefitRepository, never()).save(any());
        }
    }
}
