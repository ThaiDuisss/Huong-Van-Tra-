import { apiClient } from "../../../api/api-client";
import type {
    PageResponse,
    PromotionBenefitResponse,
    PromotionConditionResponse,
    PromotionCreateRequest,
    PromotionQuery,
    PromotionResponse,
    PromotionTargetResponse,
    PromotionUpdateRequest
} from "../types/promotion.types";

const PROMOTION_API_BASE = "/promotions";
const EVENT_PROMOTION_API_BASE = "/promotions/events";

function promotionPath(id: string) {
    return `${PROMOTION_API_BASE}/${encodeURIComponent(id)}`;
}

export const promotionApi = {
    getPromotions(params: PromotionQuery = {}) {
        return apiClient.get<PageResponse<PromotionResponse>>(
            PROMOTION_API_BASE,
            {
                query: params
            }
        );
    },

    getPromotionsByEvent(eventId: string, params: PromotionQuery = {}) {
        return apiClient.get<PageResponse<PromotionResponse>>(
            `${EVENT_PROMOTION_API_BASE}/${encodeURIComponent(
                eventId
            )}/promotions`,
            {
                query: params
            }
        );
    },

    getPromotion(promotionId: string) {
        return apiClient.get<PromotionResponse>(
            promotionPath(promotionId)
        );
    },

    getPromotionBenefits(promotionId: string) {
        return apiClient.get<PageResponse<PromotionBenefitResponse>>(
            `${promotionPath(promotionId)}/benefits`,
            {
                query: {
                    page: 0,
                    size: 100
                }
            }
        );
    },

    getPromotionTargets(promotionId: string) {
        return apiClient.get<PageResponse<PromotionTargetResponse>>(
            `${promotionPath(promotionId)}/targets`,
            {
                query: {
                    page: 0,
                    size: 100
                }
            }
        );
    },

    getPromotionConditions(promotionId: string) {
        return apiClient.get<PageResponse<PromotionConditionResponse>>(
            `${promotionPath(promotionId)}/conditions`,
            {
                query: {
                    page: 0,
                    size: 100
                }
            }
        );
    },

    createPromotion(payload: PromotionCreateRequest) {
        return apiClient.post<
            PromotionResponse,
            PromotionCreateRequest
        >(PROMOTION_API_BASE, payload);
    },

    createPromotionByEvent(
        eventId: string,
        payload: PromotionCreateRequest
    ) {
        return apiClient.post<
            PromotionResponse,
            PromotionCreateRequest
        >(
            `${EVENT_PROMOTION_API_BASE}/${encodeURIComponent(
                eventId
            )}/promotions`,
            payload
        );
    },

    updatePromotion(
        promotionId: string,
        payload: PromotionUpdateRequest
    ) {
        return apiClient.put<
            PromotionResponse,
            PromotionUpdateRequest
        >(promotionPath(promotionId), payload);
    },

    deletePromotion(promotionId: string) {
        return apiClient.delete<void>(promotionPath(promotionId));
    },

    schedulePromotion(promotionId: string) {
        return postPromotionAction(promotionId, "schedule");
    },

    activatePromotion(promotionId: string) {
        return postPromotionAction(promotionId, "activate");
    },

    pausePromotion(promotionId: string) {
        return postPromotionAction(promotionId, "pause");
    },

    resumePromotion(promotionId: string) {
        return postPromotionAction(promotionId, "resume");
    },

    cancelPromotion(promotionId: string) {
        return postPromotionAction(promotionId, "cancel");
    },

    expirePromotion(promotionId: string) {
        return postPromotionAction(promotionId, "expire");
    },

    duplicatePromotion(promotionId: string) {
        return postPromotionAction(promotionId, "duplicate");
    }
};

function postPromotionAction(promotionId: string, action: string) {
    return apiClient.post<PromotionResponse, Record<string, never>>(
        `${promotionPath(promotionId)}/${action}`,
        {}
    );
}
