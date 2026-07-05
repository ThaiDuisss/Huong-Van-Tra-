export type PromotionStatusValue =
    | "DRAFT"
    | "SCHEDULED"
    | "ACTIVE"
    | "INACTIVE"
    | "EXPIRED"
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
