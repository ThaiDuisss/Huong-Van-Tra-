import { apiClient } from "../../../api/api-client";
import type {
    PageResponse,
    PromotionBenefitRequest,
    PromotionBenefitResponse,
    PromotionConditionRequest,
    PromotionConditionResponse,
    PromotionCreateRequest,
    PromotionQuery,
    PromotionResponse,
    PromotionTargetRequest,
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

    createPromotionBenefit(
        promotionId: string,
        payload: PromotionBenefitRequest
    ) {
        return apiClient.post<
            PromotionBenefitResponse,
            PromotionBenefitRequest
        >(`${promotionPath(promotionId)}/benefits`, payload);
    },

    updatePromotionBenefit(
        promotionId: string,
        benefitId: string,
        payload: PromotionBenefitRequest
    ) {
        return apiClient.put<
            PromotionBenefitResponse,
            PromotionBenefitRequest
        >(
            `${promotionPath(promotionId)}/benefits/${encodeURIComponent(
                benefitId
            )}`,
            payload
        );
    },

    deletePromotionBenefit(promotionId: string, benefitId: string) {
        return apiClient.delete<void>(
            `${promotionPath(promotionId)}/benefits/${encodeURIComponent(
                benefitId
            )}`
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

    createPromotionTarget(
        promotionId: string,
        payload: PromotionTargetRequest
    ) {
        return apiClient.post<
            PromotionTargetResponse,
            PromotionTargetRequest
        >(`${promotionPath(promotionId)}/targets`, payload);
    },

    updatePromotionTarget(
        promotionId: string,
        targetId: string,
        payload: PromotionTargetRequest
    ) {
        return apiClient.put<
            PromotionTargetResponse,
            PromotionTargetRequest
        >(
            `${promotionPath(promotionId)}/targets/${encodeURIComponent(
                targetId
            )}`,
            payload
        );
    },

    deletePromotionTarget(promotionId: string, targetId: string) {
        return apiClient.delete<void>(
            `${promotionPath(promotionId)}/targets/${encodeURIComponent(
                targetId
            )}`
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

    createPromotionCondition(
        promotionId: string,
        payload: PromotionConditionRequest
    ) {
        return apiClient.post<
            PromotionConditionResponse,
            PromotionConditionRequest
        >(`${promotionPath(promotionId)}/conditions`, payload);
    },

    updatePromotionCondition(
        promotionId: string,
        conditionId: string,
        payload: PromotionConditionRequest
    ) {
        return apiClient.put<
            PromotionConditionResponse,
            PromotionConditionRequest
        >(
            `${promotionPath(promotionId)}/conditions/${encodeURIComponent(
                conditionId
            )}`,
            payload
        );
    },

    deletePromotionCondition(promotionId: string, conditionId: string) {
        return apiClient.delete<void>(
            `${promotionPath(promotionId)}/conditions/${encodeURIComponent(
                conditionId
            )}`
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

    endPromotion(promotionId: string) {
        return postPromotionAction(promotionId, "end");
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
