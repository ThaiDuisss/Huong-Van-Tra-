export type DiscountProgramStatus =
    | "ACTIVE"
    | "SCHEDULED"
    | "INACTIVE";

export type DiscountApplicationType =
    | "DISCOUNT_CODE"
    | "FREE_SHIPPING"
    | "BUY_X_GET_Y"
    | "AMOUNT_OFF"
    | "GIFT";

export interface DiscountProgram {
    id: string;
    name: string;
    eventName: string;
    applicationType: DiscountApplicationType;
    startAt: string;
    endAt: string;
    status: DiscountProgramStatus;
    description: string;
    goal: string;
    promotionType: string;
    canStackPromotions: boolean;
    priority: number;
    createdAt: string;
    updatedAt: string;
    version: number;
    benefit: DiscountProgramBenefit;
    conditions: DiscountCondition[];
    scopes: DiscountScope[];
}

export interface DiscountProgramFilters {
    eventName: string;
    status: "ALL" | DiscountProgramStatus;
    applicationType: "ALL" | DiscountApplicationType;
    dateRange: string;
}

export interface DiscountProgramBenefit {
    benefitType: string;
    value: string;
    maxDiscount: string;
    minProfitMargin: string;
    expectedProfitMargin: string;
    suggestedValue: string;
    confidence: string;
    aiSuggestion: string;
    reason: string;
}

export interface DiscountCondition {
    id: string;
    name: string;
    operator: string;
    value: string;
}

export interface DiscountScope {
    id: string;
    objectType: "CATEGORY" | "PRODUCT";
    target: string;
    excluded: boolean;
    overrideBenefit: string;
    overrideValue: string;
    reason: string;
}
