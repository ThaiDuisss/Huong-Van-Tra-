//package org.hvtra.promotionservice.service.impl;
//
//import lombok.AccessLevel;
//import lombok.RequiredArgsConstructor;
//import lombok.experimental.FieldDefaults;
//import org.hvtra.commonservice.exception.ResourceNotFoundException;
//import org.hvtra.promotionservice.dto.request.promotion.PromotionCreateRequest;
//import org.hvtra.promotionservice.dto.request.promotion.PromotionStatusUpdateRequest;
//import org.hvtra.promotionservice.dto.request.promotion.PromotionUpdateRequest;
//import org.hvtra.promotionservice.dto.response.PageResponse;
//import org.hvtra.promotionservice.dto.response.PromotionResponse;
//import org.hvtra.promotionservice.entity.Promotion;
//import org.hvtra.promotionservice.mapper.PromotionMapper;
//import org.hvtra.promotionservice.repository.PromotionRepository;
//import org.hvtra.promotionservice.service.PromotionService;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.data.domain.Pageable;
//import org.springframework.data.domain.Sort;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.UUID;
//
//import static org.hvtra.promotionservice.constant.event.EventErrorMessage.EVENT_NOT_FOUND_WITH_ID;
//
//@Service
//@RequiredArgsConstructor
//@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
//public class PromotionServiceImpl implements PromotionService {
//
//    PromotionRepository promotionRepository;
//    PromotionMapper promotionMapper;
//
//    @Override
//    @Transactional
//    public PromotionResponse createPromotion(PromotionCreateRequest request) {
//
//        Promotion promotion = promotionMapper.toEntity(request);
//
//        Promotion savedPromotion = promotionRepository.save(promotion);
//
//        return promotionMapper.toResponse(savedPromotion);
//    }
//
//    @Override
//    @Transactional(readOnly = true)
//    public PromotionResponse getPromotionById(UUID promotionId) {
//        Promotion promotion = promotionRepository.findByIdAndIsDeletedFalse(promotionId)
//                .orElseThrow(() -> new ResourceNotFoundException(
//                        EVENT_NOT_FOUND_WITH_ID.formatted(promotionId)
//                ));
//
//        return promotionMapper.toResponse(promotion);
//    }
//
//    @Override
//    @Transactional(readOnly = true)
//    public PageResponse<PromotionResponse> getAllPromotions(int page, int size) {
//        Pageable pageable = PageRequest.of(
//                page,
//                size,
//                Sort.by(Sort.Direction.DESC, "createdAt")
//        );
//
//        Page<PromotionResponse> promotionPage = promotionRepository.findAllByIsDeletedFalse(pageable)
//                .map(promotionMapper::toResponse);
//
//        return PageResponse.from(promotionPage);
//    }
//
//    @Override
//    @Transactional
//    public PromotionResponse updatePromotion(
//            UUID promotionId, PromotionUpdateRequest request
//    ) {
//        Promotion promotion = promotionRepository.findByIdAndIsDeletedFalse(promotionId)
//                .orElseThrow(() -> new ResourceNotFoundException(
//                        EVENT_NOT_FOUND_WITH_ID.formatted(promotionId)
//                ));
//
//        promotionMapper.updateEntityFromRequest(request, promotion);
//
//        Promotion updatedPromotion = promotionRepository.save(promotion);
//
//        return promotionMapper.toResponse(updatedPromotion);
//    }
//
//    @Override
//    @Transactional
//    public PromotionResponse updatePromotionStatus(
//            UUID promotionId, PromotionStatusUpdateRequest request
//    ) {
//        Promotion promotion = promotionRepository.findByIdAndIsDeletedFalse(promotionId)
//                .orElseThrow(() -> new ResourceNotFoundException(
//                        EVENT_NOT_FOUND_WITH_ID.formatted(promotionId)
//                ));
//
//        promotion.setStatus(request.getStatus());
//
//        Promotion updatedPromotion = promotionRepository.save(promotion);
//
//        return promotionMapper.toResponse(updatedPromotion);
//    }
//
//    @Override
//    @Transactional
//    public void deletePromotion(UUID promotionId) {
//        Promotion promotion = promotionRepository.findByIdAndIsDeletedFalse(promotionId)
//                .orElseThrow(() -> new ResourceNotFoundException(
//                        EVENT_NOT_FOUND_WITH_ID.formatted(promotionId)
//                ));
//
//        promotion.setIsDeleted(true);
//
//        promotionRepository.save(promotion);
//    }
//
//}
