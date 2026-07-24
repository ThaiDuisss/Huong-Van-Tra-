import { apiClient } from "../../../api/api-client";
import type {
    CouponPageResponse,
    CouponQuery,
    CouponResponse,
    CouponUsagePageResponse,
    CouponUsageQuery,
    CouponUsageResponse
} from "../types/coupon.types";

const COUPON_API_BASE = "/promotions/coupon-usages";
const COUPON_USAGE_API_BASE = `${COUPON_API_BASE}/`;

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
