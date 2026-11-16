package org.hvtra.promotionservice.service;

import org.hvtra.commonservice.common_core.exception.BusinessException;
import org.hvtra.commonservice.common_core.exception.ResourceNotFoundException;
import org.hvtra.promotionservice.dto.request.promotiontarget.PromotionTargetCreateRequest;
import org.hvtra.promotionservice.dto.request.promotiontarget.PromotionTargetUpdateRequest;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.dto.response.PromotionTargetResponse;
import org.hvtra.promotionservice.entity.Promotion;
import org.hvtra.promotionservice.entity.PromotionTarget;
import org.hvtra.promotionservice.enums.promotion.PromotionTargetType;
import org.hvtra.promotionservice.mapper.PromotionTargetMapper;
import org.hvtra.promotionservice.repository.promotion.PromotionRepository;
import org.hvtra.promotionservice.repository.promotion.PromotionTargetRepository;
import org.hvtra.promotionservice.service.impl.PromotionTargetServiceImpl;
import org.hvtra.promotionservice.validation.validator.PromotionTargetValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PromotionTargetServiceImpl")
class PromotionTargetServiceImplTest {

    @Mock
    PromotionRepository promotionRepository;

    @Mock
    PromotionTargetRepository promotionTargetRepository;

    @Mock
    PromotionTargetMapper promotionTargetMapper;

    @Mock
    PromotionTargetValidator promotionTargetValidator;

    @InjectMocks
    PromotionTargetServiceImpl promotionTargetService;

    UUID promotionId;
    UUID targetId;
    Promotion promotion;
    PromotionTarget target;
    PromotionTargetResponse response;

    @BeforeEach
    void setUp() {
        promotionId = UUID.randomUUID();
        targetId = UUID.randomUUID();
        promotion = Promotion.builder().name("P").build();
        promotion.setId(promotionId);
        target = PromotionTarget.builder().targetType(PromotionTargetType.PRODUCT).targetId(UUID.randomUUID()).build();
        target.setId(targetId);
        response = PromotionTargetResponse.builder().id(targetId).build();
    }

    @Nested
    @DisplayName("createTarget")
    class CreateTarget {

        @Test
        @DisplayName("attaches the promotion and defaults excluded to false")
        void createTarget_setsPromotionAndExcludedDefault() {
            PromotionTargetCreateRequest request = PromotionTargetCreateRequest.builder()
                    .targetType(PromotionTargetType.CATEGORY).targetId(UUID.randomUUID()).build();
            PromotionTarget entity = PromotionTarget.builder().targetType(PromotionTargetType.CATEGORY).excluded(null).build();
            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId)).thenReturn(Optional.of(promotion));
            when(promotionTargetMapper.toEntity(request)).thenReturn(entity);
            when(promotionTargetRepository.save(entity)).thenReturn(entity);
            when(promotionTargetMapper.toResponse(entity)).thenReturn(response);

            PromotionTargetResponse result = promotionTargetService.createTarget(promotionId, request);

            assertThat(result).isSameAs(response);
            assertThat(entity.getPromotion()).isSameAs(promotion);
            assertThat(entity.getExcluded()).isFalse();
            verify(promotionTargetValidator).validateBeforeCreate(promotion, request);
        }

        @Test
        @DisplayName("keeps excluded=true when the request excludes the target")
        void createTarget_keepsExcludedTrue() {
            PromotionTargetCreateRequest request = PromotionTargetCreateRequest.builder()
                    .targetType(PromotionTargetType.PRODUCT).targetId(UUID.randomUUID()).excluded(true).build();
            PromotionTarget entity = PromotionTarget.builder().excluded(true).build();
            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId)).thenReturn(Optional.of(promotion));
            when(promotionTargetMapper.toEntity(request)).thenReturn(entity);
            when(promotionTargetRepository.save(entity)).thenReturn(entity);
            when(promotionTargetMapper.toResponse(entity)).thenReturn(response);

            promotionTargetService.createTarget(promotionId, request);

            assertThat(entity.getExcluded()).isTrue();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the promotion is missing")
        void createTarget_missingPromotion_throws() {
            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> promotionTargetService.createTarget(promotionId,
                    PromotionTargetCreateRequest.builder().build()))
                    .isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(promotionTargetValidator, promotionTargetMapper, promotionTargetRepository);
        }

        @Test
        @DisplayName("does not save when the validator rejects the target")
        void createTarget_validatorRejects_noSave() {
            PromotionTargetCreateRequest request = PromotionTargetCreateRequest.builder().build();
            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId)).thenReturn(Optional.of(promotion));
            doThrow(new BusinessException("Promotion target already exists"))
                    .when(promotionTargetValidator).validateBeforeCreate(promotion, request);

            assertThatThrownBy(() -> promotionTargetService.createTarget(promotionId, request))
                    .isInstanceOf(BusinessException.class);
            verifyNoInteractions(promotionTargetMapper, promotionTargetRepository);
        }
    }

    @Nested
    @DisplayName("read")
    class Read {

        @Test
        @DisplayName("getTargetsByPromotion returns the mapped page sorted by createdAt desc")
        void getTargetsByPromotion_returnsPage() {
            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId)).thenReturn(Optional.of(promotion));
            when(promotionTargetRepository.findAllByPromotionIdAndIsDeletedFalse(eq(promotionId), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(target)));
            when(promotionTargetMapper.toResponse(target)).thenReturn(response);

            PageResponse<PromotionTargetResponse> result = promotionTargetService.getTargetsByPromotion(promotionId, 2, 5);

            assertThat(result.getContent()).containsExactly(response);
            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(promotionTargetRepository).findAllByPromotionIdAndIsDeletedFalse(eq(promotionId), pageable.capture());
            assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
            assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
            assertThat(pageable.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
        }

        @Test
        @DisplayName("getTargetById returns the target of the promotion")
        void getTargetById_returnsTarget() {
            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId)).thenReturn(Optional.of(promotion));
            when(promotionTargetRepository.findByIdAndPromotionIdAndIsDeletedFalse(targetId, promotionId))
                    .thenReturn(Optional.of(target));
            when(promotionTargetMapper.toResponse(target)).thenReturn(response);

            assertThat(promotionTargetService.getTargetById(promotionId, targetId)).isSameAs(response);
        }

        @Test
        @DisplayName("getTargetById throws when the target belongs to another promotion or is deleted")
        void getTargetById_missing_throws() {
            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId)).thenReturn(Optional.of(promotion));
            when(promotionTargetRepository.findByIdAndPromotionIdAndIsDeletedFalse(targetId, promotionId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> promotionTargetService.getTargetById(promotionId, targetId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(targetId.toString());
        }

        @Test
        @DisplayName("read operations throw when the promotion is missing")
        void read_missingPromotion_throws() {
            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> promotionTargetService.getTargetsByPromotion(promotionId, 0, 10))
                    .isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> promotionTargetService.getTargetById(promotionId, targetId))
                    .isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(promotionTargetRepository);
        }
    }

    @Nested
    @DisplayName("update and delete")
    class UpdateAndDelete {

        @Test
        @DisplayName("updateTarget validates, applies the mapper and defaults excluded")
        void updateTarget_appliesMapper() {
            PromotionTargetUpdateRequest request = PromotionTargetUpdateRequest.builder()
                    .targetType(PromotionTargetType.PRODUCT).targetId(target.getTargetId()).build();
            target.setExcluded(null);
            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId)).thenReturn(Optional.of(promotion));
            when(promotionTargetRepository.findByIdAndPromotionIdAndIsDeletedFalse(targetId, promotionId))
                    .thenReturn(Optional.of(target));
            when(promotionTargetRepository.save(target)).thenReturn(target);
            when(promotionTargetMapper.toResponse(target)).thenReturn(response);

            PromotionTargetResponse result = promotionTargetService.updateTarget(promotionId, targetId, request);

            assertThat(result).isSameAs(response);
            assertThat(target.getExcluded()).isFalse();
            verify(promotionTargetValidator).validateBeforeUpdate(promotion, targetId, request);
            verify(promotionTargetMapper).updateEntityFromRequest(request, target);
        }

        @Test
        @DisplayName("updateTarget validates before loading the target")
        void updateTarget_validatorRejects_noLoad() {
            PromotionTargetUpdateRequest request = PromotionTargetUpdateRequest.builder().build();
            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId)).thenReturn(Optional.of(promotion));
            doThrow(new BusinessException("Cannot modify targets of active promotion"))
                    .when(promotionTargetValidator).validateBeforeUpdate(promotion, targetId, request);

            assertThatThrownBy(() -> promotionTargetService.updateTarget(promotionId, targetId, request))
                    .isInstanceOf(BusinessException.class);
            verifyNoInteractions(promotionTargetRepository, promotionTargetMapper);
        }

        @Test
        @DisplayName("deleteTarget soft deletes the target")
        void deleteTarget_softDeletes() {
            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId)).thenReturn(Optional.of(promotion));
            when(promotionTargetRepository.findByIdAndPromotionIdAndIsDeletedFalse(targetId, promotionId))
                    .thenReturn(Optional.of(target));

            promotionTargetService.deleteTarget(promotionId, targetId);

            assertThat(target.getIsDeleted()).isTrue();
            verify(promotionTargetValidator).validateBeforeDelete(promotion);
            verify(promotionTargetRepository).save(target);
        }

        @Test
        @DisplayName("deleteTarget throws when the target is missing and saves nothing")
        void deleteTarget_missing_throws() {
            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId)).thenReturn(Optional.of(promotion));
            when(promotionTargetRepository.findByIdAndPromotionIdAndIsDeletedFalse(targetId, promotionId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> promotionTargetService.deleteTarget(promotionId, targetId))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(promotionTargetRepository, never()).save(any());
        }
    }
}
