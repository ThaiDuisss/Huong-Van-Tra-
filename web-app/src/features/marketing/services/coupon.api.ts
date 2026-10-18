import { apiClient } from "../../../api/api-client";
import type {
    CouponPageResponse,
    CouponQuery,
    CouponResponse,
    CouponSaveRequest,
    CouponStatusValue,
    CouponUsagePageResponse,
    CouponUsageQuery,
    CouponUsageResponse
} from "../types/coupon.types";

const COUPON_API_BASE = "/promotions";
const COUPON_USAGE_API_BASE = `${COUPON_API_BASE}/coupon-usages`;

function couponPath(couponId: string) {
    return `${COUPON_API_BASE}/coupons/${encodeURIComponent(couponId)}`;
}

export const couponApi = {
    list(query: CouponQuery = {}) {
        const params = {
            page: query.page ?? 0,
            size: query.size ?? 10
        };

        if (query.promotionId) {
            return apiClient.get<CouponPageResponse>(
                `${COUPON_API_BASE}/promotions/${encodeURIComponent(
                    query.promotionId
                )}/coupons`,
                {
                    query: params
                }
            );
        }

        return apiClient.get<CouponPageResponse>(
            `${COUPON_API_BASE}/coupons`,
            {
                query: params
            }
        );
    },

    detail(couponId: string) {
        return apiClient.get<CouponResponse>(couponPath(couponId));
    },

    create(request: CouponSaveRequest) {
        return apiClient.post<CouponResponse, CouponSaveRequest>(
            `${COUPON_API_BASE}/coupons`,
            request
        );
    },

    update(couponId: string, request: CouponSaveRequest) {
        return apiClient.put<CouponResponse, CouponSaveRequest>(
            couponPath(couponId),
            request
        );
    },

    updateStatus(couponId: string, status: CouponStatusValue) {
        return apiClient.patch<CouponResponse, { status: CouponStatusValue }>(
            `${couponPath(couponId)}/status`,
            { status }
        );
    },

    usages(query: CouponUsageQuery = {}) {
        const params = {
            page: query.page ?? 0,
            size: query.size ?? 10
        };

        if (query.couponId) {
            return apiClient.get<CouponUsagePageResponse>(
                `${COUPON_API_BASE}/coupons/${encodeURIComponent(
                    query.couponId
                )}/usages`,
                {
                    query: params
                }
            );
        }

        return apiClient.get<CouponUsagePageResponse>(
            COUPON_USAGE_API_BASE,
            {
                query: params
            }
        );
    },

    usageDetail(usageId: string) {
        return apiClient.get<CouponUsageResponse>(
            `${COUPON_USAGE_API_BASE}/${encodeURIComponent(usageId)}`
        );
    }
};
