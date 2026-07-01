//package org.hvtra.promotionservice.service;
//
//import org.hvtra.commonservice.exception.ResourceNotFoundException;
//import org.hvtra.promotionservice.dto.request.promotioncondition.PromotionConditionCreateRequest;
//import org.hvtra.promotionservice.dto.request.promotioncondition.PromotionConditionUpdateRequest;
//import org.hvtra.promotionservice.dto.response.PageResponse;
//import org.hvtra.promotionservice.dto.response.PromotionConditionResponse;
//import org.hvtra.promotionservice.entity.Promotion;
//import org.hvtra.promotionservice.entity.PromotionCondition;
//import org.hvtra.promotionservice.mapper.PromotionConditionMapper;
//import org.hvtra.promotionservice.repository.PromotionConditionRepository;
//import org.hvtra.promotionservice.repository.PromotionRepository;
//import org.hvtra.promotionservice.service.impl.PromotionConditionServiceImpl;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Nested;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.ArgumentCaptor;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.PageImpl;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.data.domain.Pageable;
//
//import java.util.List;
//import java.util.Optional;
//import java.util.UUID;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.junit.jupiter.api.Assertions.assertThrows;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//class PromotionConditionServiceImplTest {
//
//    @Mock
//    PromotionRepository promotionRepository;
//
//    @Mock
//    PromotionConditionRepository promotionConditionRepository;
//
//    @Mock
//    PromotionConditionMapper promotionConditionMapper;
//
//    @InjectMocks
//    PromotionConditionServiceImpl promotionConditionService;
//
//    @Nested
//    @DisplayName("createCondition")
//    class CreateConditionTest {
//
//        @Test
//        @DisplayName("Tạo condition thành công")
//        void createCondition_success() {
//            UUID promotionId = UUID.randomUUID();
//
//            PromotionConditionCreateRequest request =
//                    mock(PromotionConditionCreateRequest.class);
//
//            Promotion promotion = new Promotion();
//            promotion.setId(promotionId);
//
//            PromotionCondition condition = new PromotionCondition();
//
//            PromotionCondition savedCondition = new PromotionCondition();
//            savedCondition.setId(UUID.randomUUID());
//            savedCondition.setPromotion(promotion);
//
//            PromotionConditionResponse expectedResponse =
//                    mock(PromotionConditionResponse.class);
//
//            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
//                    .thenReturn(Optional.of(promotion));
//
//            when(promotionConditionMapper.toEntity(request))
//                    .thenReturn(condition);
//
//            when(promotionConditionRepository.save(any(PromotionCondition.class)))
//                    .thenReturn(savedCondition);
//
//            when(promotionConditionMapper.toResponse(savedCondition))
//                    .thenReturn(expectedResponse);
//
//            PromotionConditionResponse actualResponse =
//                    promotionConditionService.createCondition(promotionId, request);
//
//            assertThat(actualResponse).isEqualTo(expectedResponse);
//
//            ArgumentCaptor<PromotionCondition> captor =
//                    ArgumentCaptor.forClass(PromotionCondition.class);
//
//            verify(promotionConditionRepository).save(captor.capture());
//
//            PromotionCondition savedArgument = captor.getValue();
//
//            assertThat(savedArgument.getPromotion()).isEqualTo(promotion);
//
//            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
//            verify(promotionConditionMapper).toEntity(request);
//            verify(promotionConditionMapper).toResponse(savedCondition);
//        }
//
//        @Test
//        @DisplayName("Tạo condition thất bại khi promotion không tồn tại")
//        void createCondition_promotionNotFound_throwException() {
//            UUID promotionId = UUID.randomUUID();
//
//            PromotionConditionCreateRequest request =
//                    mock(PromotionConditionCreateRequest.class);
//
//            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
//                    .thenReturn(Optional.empty());
//
//            assertThrows(
//                    ResourceNotFoundException.class,
//                    () -> promotionConditionService.createCondition(promotionId, request)
//            );
//
//            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
//            verifyNoInteractions(promotionConditionMapper);
//            verifyNoInteractions(promotionConditionRepository);
//        }
//    }
//
//    @Nested
//    @DisplayName("getConditionsByPromotion")
//    class GetConditionsByPromotionTest {
//
//        @Test
//        @DisplayName("Lấy danh sách condition theo promotion thành công")
//        void getConditionsByPromotion_success() {
//            UUID promotionId = UUID.randomUUID();
//
//            int page = 0;
//            int size = 10;
//
//            Promotion promotion = new Promotion();
//            promotion.setId(promotionId);
//
//            PromotionCondition condition1 = new PromotionCondition();
//            condition1.setId(UUID.randomUUID());
//            condition1.setPromotion(promotion);
//
//            PromotionCondition condition2 = new PromotionCondition();
//            condition2.setId(UUID.randomUUID());
//            condition2.setPromotion(promotion);
//
//            PromotionConditionResponse response1 =
//                    mock(PromotionConditionResponse.class);
//
//            PromotionConditionResponse response2 =
//                    mock(PromotionConditionResponse.class);
//
//            Pageable pageable = PageRequest.of(page, size);
//
//            Page<PromotionCondition> conditionPage =
//                    new PageImpl<>(
//                            List.of(condition1, condition2),
//                            pageable,
//                            2
//                    );
//
//            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
//                    .thenReturn(Optional.of(promotion));
//
//            when(promotionConditionRepository.findByPromotionAndIsDeletedFalse(
//                    promotion,
//                    pageable
//            )).thenReturn(conditionPage);
//
//            when(promotionConditionMapper.toResponse(condition1))
//                    .thenReturn(response1);
//
//            when(promotionConditionMapper.toResponse(condition2))
//                    .thenReturn(response2);
//
//            PageResponse<PromotionConditionResponse> actualResponse =
//                    promotionConditionService.getConditionsByPromotion(
//                            promotionId,
//                            page,
//                            size
//                    );
//
//            assertThat(actualResponse).isNotNull();
//
//            assertThat(actualResponse.getContent())
//                    .containsExactly(response1, response2);
//
//            assertThat(actualResponse.getPage()).isZero();
//            assertThat(actualResponse.getSize()).isEqualTo(10);
//            assertThat(actualResponse.getTotalElements()).isEqualTo(2);
//            assertThat(actualResponse.getTotalPages()).isEqualTo(1);
//            assertThat(actualResponse.isFirst()).isTrue();
//            assertThat(actualResponse.isLast()).isTrue();
//            assertThat(actualResponse.isEmpty()).isFalse();
//
//            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
//
//            verify(promotionConditionRepository)
//                    .findByPromotionAndIsDeletedFalse(promotion, pageable);
//
//            verify(promotionConditionMapper).toResponse(condition1);
//            verify(promotionConditionMapper).toResponse(condition2);
//        }
//
//        @Test
//        @DisplayName("Lấy danh sách condition thất bại khi promotion không tồn tại")
//        void getConditionsByPromotion_promotionNotFound_throwException() {
//            UUID promotionId = UUID.randomUUID();
//
//            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
//                    .thenReturn(Optional.empty());
//
//            assertThrows(
//                    ResourceNotFoundException.class,
//                    () -> promotionConditionService.getConditionsByPromotion(
//                            promotionId,
//                            0,
//                            10
//                    )
//            );
//
//            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
//            verifyNoInteractions(promotionConditionRepository);
//            verifyNoInteractions(promotionConditionMapper);
//        }
//    }
//
//    @Nested
//    @DisplayName("updateCondition")
//    class UpdateConditionTest {
//
//        @Test
//        @DisplayName("Cập nhật condition thành công")
//        void updateCondition_success() {
//            UUID promotionId = UUID.randomUUID();
//            UUID conditionId = UUID.randomUUID();
//
//            PromotionConditionUpdateRequest request =
//                    mock(PromotionConditionUpdateRequest.class);
//
//            Promotion promotion = new Promotion();
//            promotion.setId(promotionId);
//
//            PromotionCondition condition = new PromotionCondition();
//            condition.setId(conditionId);
//            condition.setPromotion(promotion);
//
//            PromotionCondition updatedCondition = new PromotionCondition();
//            updatedCondition.setId(conditionId);
//            updatedCondition.setPromotion(promotion);
//
//            PromotionConditionResponse expectedResponse =
//                    mock(PromotionConditionResponse.class);
//
//            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
//                    .thenReturn(Optional.of(promotion));
//
//            when(promotionConditionRepository.findByIdAndPromotionIdAndIsDeletedFalse(
//                    conditionId,
//                    promotionId
//            )).thenReturn(Optional.of(condition));
//
//            when(promotionConditionRepository.save(condition))
//                    .thenReturn(updatedCondition);
//
//            when(promotionConditionMapper.toResponse(updatedCondition))
//                    .thenReturn(expectedResponse);
//
//            PromotionConditionResponse actualResponse =
//                    promotionConditionService.updateCondition(
//                            promotionId,
//                            conditionId,
//                            request
//                    );
//
//            assertThat(actualResponse).isEqualTo(expectedResponse);
//
//            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
//
//            verify(promotionConditionRepository)
//                    .findByIdAndPromotionIdAndIsDeletedFalse(conditionId, promotionId);
//
//            verify(promotionConditionMapper)
//                    .updateEntityFromRequest(request, condition);
//
//            verify(promotionConditionRepository).save(condition);
//            verify(promotionConditionMapper).toResponse(updatedCondition);
//        }
//
//        @Test
//        @DisplayName("Cập nhật condition thất bại khi promotion không tồn tại")
//        void updateCondition_promotionNotFound_throwException() {
//            UUID promotionId = UUID.randomUUID();
//            UUID conditionId = UUID.randomUUID();
//
//            PromotionConditionUpdateRequest request =
//                    mock(PromotionConditionUpdateRequest.class);
//
//            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
//                    .thenReturn(Optional.empty());
//
//            assertThrows(
//                    ResourceNotFoundException.class,
//                    () -> promotionConditionService.updateCondition(
//                            promotionId,
//                            conditionId,
//                            request
//                    )
//            );
//
//            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
//            verifyNoInteractions(promotionConditionRepository);
//            verifyNoInteractions(promotionConditionMapper);
//        }
//
//        @Test
//        @DisplayName("Cập nhật condition thất bại khi condition không thuộc promotion")
//        void updateCondition_conditionNotFoundInPromotion_throwException() {
//            UUID promotionId = UUID.randomUUID();
//            UUID conditionId = UUID.randomUUID();
//
//            PromotionConditionUpdateRequest request =
//                    mock(PromotionConditionUpdateRequest.class);
//
//            Promotion promotion = new Promotion();
//            promotion.setId(promotionId);
//
//            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
//                    .thenReturn(Optional.of(promotion));
//
//            when(promotionConditionRepository.findByIdAndPromotionIdAndIsDeletedFalse(
//                    conditionId,
//                    promotionId
//            )).thenReturn(Optional.empty());
//
//            assertThrows(
//                    ResourceNotFoundException.class,
//                    () -> promotionConditionService.updateCondition(
//                            promotionId,
//                            conditionId,
//                            request
//                    )
//            );
//
//            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
//
//            verify(promotionConditionRepository)
//                    .findByIdAndPromotionIdAndIsDeletedFalse(conditionId, promotionId);
//
//            verifyNoInteractions(promotionConditionMapper);
//            verify(promotionConditionRepository, never()).save(any());
//        }
//    }
//
//    @Nested
//    @DisplayName("deleteCondition")
//    class DeleteConditionTest {
//
//        @Test
//        @DisplayName("Xóa mềm condition thành công")
//        void deleteCondition_success() {
//            UUID promotionId = UUID.randomUUID();
//            UUID conditionId = UUID.randomUUID();
//
//            Promotion promotion = new Promotion();
//            promotion.setId(promotionId);
//
//            PromotionCondition condition = new PromotionCondition();
//            condition.setId(conditionId);
//            condition.setPromotion(promotion);
//            condition.setIsDeleted(false);
//
//            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
//                    .thenReturn(Optional.of(promotion));
//
//            when(promotionConditionRepository.findByIdAndPromotionIdAndIsDeletedFalse(
//                    conditionId,
//                    promotionId
//            )).thenReturn(Optional.of(condition));
//
//            promotionConditionService.deleteCondition(promotionId, conditionId);
//
//            assertThat(condition.getIsDeleted()).isTrue();
//
//            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
//
//            verify(promotionConditionRepository)
//                    .findByIdAndPromotionIdAndIsDeletedFalse(conditionId, promotionId);
//
//            verify(promotionConditionRepository).save(condition);
//        }
//
//        @Test
//        @DisplayName("Xóa condition thất bại khi promotion không tồn tại")
//        void deleteCondition_promotionNotFound_throwException() {
//            UUID promotionId = UUID.randomUUID();
//            UUID conditionId = UUID.randomUUID();
//
//            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
//                    .thenReturn(Optional.empty());
//
//            assertThrows(
//                    ResourceNotFoundException.class,
//                    () -> promotionConditionService.deleteCondition(
//                            promotionId,
//                            conditionId
//                    )
//            );
//
//            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
//            verifyNoInteractions(promotionConditionRepository);
//        }
//
//        @Test
//        @DisplayName("Xóa condition thất bại khi condition không thuộc promotion")
//        void deleteCondition_conditionNotFoundInPromotion_throwException() {
//            UUID promotionId = UUID.randomUUID();
//            UUID conditionId = UUID.randomUUID();
//
//            Promotion promotion = new Promotion();
//            promotion.setId(promotionId);
//
//            when(promotionRepository.findByIdAndIsDeletedFalse(promotionId))
//                    .thenReturn(Optional.of(promotion));
//
//            when(promotionConditionRepository.findByIdAndPromotionIdAndIsDeletedFalse(
//                    conditionId,
//                    promotionId
//            )).thenReturn(Optional.empty());
//
//            assertThrows(
//                    ResourceNotFoundException.class,
//                    () -> promotionConditionService.deleteCondition(
//                            promotionId,
//                            conditionId
//                    )
//            );
//
//            verify(promotionRepository).findByIdAndIsDeletedFalse(promotionId);
//
//            verify(promotionConditionRepository)
//                    .findByIdAndPromotionIdAndIsDeletedFalse(conditionId, promotionId);
//
//            verify(promotionConditionRepository, never()).save(any());
//        }
//    }
//}
