//package org.hvtra.promotionservice.service;
//
//import org.hvtra.promotionservice.dto.request.promotion.PromotionCreateRequest;
//import org.hvtra.promotionservice.dto.request.promotion.PromotionStatusUpdateRequest;
//import org.hvtra.promotionservice.dto.request.promotion.PromotionUpdateRequest;
//import org.hvtra.promotionservice.dto.response.PageResponse;
//import org.hvtra.promotionservice.dto.response.PromotionResponse;
//
//import java.util.UUID;
//
//public interface PromotionService {
//
//    PromotionResponse createPromotion(PromotionCreateRequest request);
//
//    PromotionResponse getPromotionById(UUID promotionId);
//
//    PageResponse<PromotionResponse> getAllPromotions(int page, int size);
//
//    PromotionResponse updatePromotion(UUID promotionId, PromotionUpdateRequest request);
//
//    PromotionResponse updatePromotionStatus(UUID promotionId, PromotionStatusUpdateRequest request);
//
//    void deletePromotion(UUID promotionId);
//
//}
