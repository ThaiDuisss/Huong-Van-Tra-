import type { PageResponse } from "./promotion.types";

export type CouponStatusValue =
    | "DRAFT"
    | "SCHEDULED"
    | "ACTIVE"
    | "PAUSED"
    | "EXPIRED"
    | "CANCELLED"
    | "ARCHIVED";

export type CouponScopeValue = "PUBLIC" | "PERSONAL";

export type CouponUsageStatusValue =
    | "RESERVED"
    | "APPLIED"
    | "RELEASED"
    | "EXPIRED"
    | "CANCELLED";

export interface CouponResponse {
    id: string;
    promotionId: string;
    promotionName?: string;
    scope: CouponScopeValue;
    customerId?: string;
    code: string;
    issueReason?: string;
    sourceOrderId?: string;
    issuedAt?: string;
    claimedAt?: string;
    usageLimit?: number;
    usedCount?: number;
    expiresAt?: string;
    status: CouponStatusValue;
    snapshotPromotionName?: string;
    snapshotBenefitType?: string;
    snapshotDiscountValue?: number | string;
    snapshotMaxDiscount?: number | string;
    createdAt?: string;
    updatedAt?: string;
}

export interface CouponUsageResponse {
    id: string;
    couponId: string;
    couponCode?: string;
    orderId: string;
    customerId?: string;
    discountAmount?: number | string;
    status: CouponUsageStatusValue;
    reservationToken?: string;
    reservedAt?: string;
    reservationExpiresAt?: string;
    usedAt?: string;
    releasedAt?: string;
    branchId?: string;
    cashierId?: string;
    channel?: string;
    createdAt?: string;
    updatedAt?: string;
}

export interface CouponQuery {
    page?: number;
    size?: number;
    promotionId?: string;
}

export interface CouponUsageQuery {
    page?: number;
    size?: number;
    couponId?: string;
}

export type CouponPageResponse = PageResponse<CouponResponse>;
export type CouponUsagePageResponse = PageResponse<CouponUsageResponse>;
