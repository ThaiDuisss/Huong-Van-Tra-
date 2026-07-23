import { apiClient } from "../../../api/api-client";

export type PromotionChannel = "POS" | "ONLINE" | "APP" | "WEB";

export interface PromotionPreviewRequest {
    orderAmount: number;
    customerId?: string;
    branchId?: string;
    channel: PromotionChannel;
    couponCode?: string;
}

export interface AvailablePromotionResponse {
    promotionId: string;
    promotionName: string;
    applyMethod?: string;
    benefitType?: string;
    estimatedDiscount?: number;
    stackable?: boolean;
    requiresCoupon?: boolean;
    couponId?: string;
    couponCode?: string;
    couponScope?: string;
    couponExpiresAt?: string;
}

export interface PromotionPreviewResponse {
    orderAmount: number;
    availablePromotions: AvailablePromotionResponse[];
}

export interface CouponUsageReserveRequest {
    couponCode: string;
    orderId: string;
    customerId: string;
    discountAmount: number;
    branchId?: string;
    cashierId?: string;
    channel?: PromotionChannel;
}

export interface CouponUsageResponse {
    id: string;
    couponId: string;
    couponCode: string;
    orderId: string;
    customerId: string;
    discountAmount: number;
    status: string;
    reservationToken?: string;
    branchId?: string;
    cashierId?: string;
    channel?: PromotionChannel;
}

export const posPromotionApi = {
    preview(input: PromotionPreviewRequest) {
        return apiClient.post<PromotionPreviewResponse, PromotionPreviewRequest>(
            "/promotions/preview",
            input
        );
    },

    reserveCoupon(input: CouponUsageReserveRequest) {
        return apiClient.post<CouponUsageResponse, CouponUsageReserveRequest>(
            "/promotions/coupon-usages/reserve",
            input
        );
    },

    applyCouponUsage(usageId: string) {
        return apiClient.post<CouponUsageResponse, Record<string, never>>(
            `/promotions/coupon-usages/${encodeURIComponent(usageId)}/apply`,
            {}
        );
    },

    releaseCouponUsage(usageId: string) {
        return apiClient.post<CouponUsageResponse, Record<string, never>>(
            `/promotions/coupon-usages/${encodeURIComponent(usageId)}/release`,
            {}
        );
    },

    cancelCouponUsage(usageId: string) {
        return apiClient.post<CouponUsageResponse, Record<string, never>>(
            `/promotions/coupon-usages/${encodeURIComponent(usageId)}/cancel`,
            {}
        );
    },

    expireCouponUsage(usageId: string) {
        return apiClient.post<CouponUsageResponse, Record<string, never>>(
            `/promotions/coupon-usages/${encodeURIComponent(usageId)}/expire`,
            {}
        );
    }
};
