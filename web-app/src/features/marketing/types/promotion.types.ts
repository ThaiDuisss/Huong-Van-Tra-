export type PromotionStatusValue =
    | "DRAFT"
    | "SCHEDULED"
    | "ACTIVE"
    | "PAUSED"
    | "EXPIRED"
    | "ENDED"
    | "CANCELLED";

export type PromotionTypeValue =
    | "PERCENT_DISCOUNT"
    | "FIXED_DISCOUNT"
    | "FREE_SHIPPING"
    | "BUY_X_GET_Y"
    | "FREE_PRODUCT";

export type PromotionApplyMethodValue =
    | "AUTO"
    | "COUPON_CODE"
    | "DIRECT_DISCOUNT";

export interface PromotionResponse {
    id: string;
    eventId: string;
    eventName?: string;
    name: string;
    description?: string;
    objective?: string;
    type: PromotionTypeValue;
    applyMethod: PromotionApplyMethodValue;
    startAt: string;
    endAt: string;
    stackable?: boolean;
    priority?: number;
    status: PromotionStatusValue;
    isDeleted?: boolean;
    createdAt?: string;
    updatedAt?: string;
    version?: number;
}

export interface PromotionBenefitResponse {
    id: string;
    promotionId: string;
    benefitType: string;
    value?: number | string;
    maxDiscount?: number | string;
    giftProductId?: string;
    buyQuantity?: number;
    getQuantity?: number;
    reason?: string;
    minMarginPercent?: number | string;
    expectedMarginPercent?: number | string;
    recommendedValue?: number | string;
    recommendReason?: string;
    confidenceScore?: number | string;
    aiRecommended?: boolean;
}

export interface PromotionBenefitRequest {
    benefitType: string;
    value: number | string;
    maxDiscount?: number | string;
    giftProductId?: string;
    buyQuantity?: number;
    getQuantity?: number;
    reason?: string;
    minMarginPercent?: number | string;
    expectedMarginPercent?: number | string;
}

export interface PromotionTargetResponse {
    id: string;
    promotionId: string;
    targetType: string;
    targetId: string;
    importBatchId?: string;
    excluded?: boolean;
    overrideBenefitType?: string;
    overrideValue?: number | string;
    overrideMaxDiscount?: number | string;
    reason?: string;
}

export interface PromotionTargetRequest {
    targetType: string;
    targetId: string;
    importBatchId?: string;
    excluded?: boolean;
    overrideBenefitType?: string;
    overrideValue?: number | string;
    overrideMaxDiscount?: number | string;
    reason?: string;
}

export interface PromotionConditionResponse {
    id: string;
    promotionId: string;
    conditionType: string;
    operator: string;
    conditionValue: string;
}

export interface PromotionConditionRequest {
    conditionType: string;
    operator: string;
    conditionValue: string;
}

export interface PromotionCreateRequest {
    eventId?: string;
    name: string;
    description?: string;
    objective?: string;
    type: PromotionTypeValue | "";
    applyMethod: PromotionApplyMethodValue | "";
    startAt: string;
    endAt: string;
    stackable?: boolean;
    priority?: number;
}

export type PromotionUpdateRequest = Omit<
    PromotionCreateRequest,
    "eventId"
>;

export interface PromotionQuery {
    [key: string]: string | number | boolean | undefined;
    page?: number;
    size?: number;
    keyword?: string;
    eventId?: string;
    type?: PromotionTypeValue;
    applyMethod?: PromotionApplyMethodValue;
    status?: PromotionStatusValue;
    startFrom?: string;
    startTo?: string;
    endFrom?: string;
    endTo?: string;
}

export interface PageResponse<T> {
    content: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    first: boolean;
    last: boolean;
    empty: boolean;
}
