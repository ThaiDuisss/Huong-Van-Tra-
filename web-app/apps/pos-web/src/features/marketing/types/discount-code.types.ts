export type DiscountCodeStatus =
    | "ACTIVE"
    | "USED"
    | "SCHEDULED"
    | "EXPIRED";

export type DiscountCodeScope = "PUBLIC" | "PERSONAL";

export interface DiscountCode {
    id: string;
    code: string;
    issueType: string;
    programName: string;
    scope: DiscountCodeScope;
    customerName?: string;
    startAt: string;
    endAt: string;
    usedCount: number;
    usageLimit: number;
    status: DiscountCodeStatus;
}

export interface DiscountCodeFilters {
    programName: string;
    status: "ALL" | DiscountCodeStatus;
    scope: "ALL" | DiscountCodeScope;
    dateRange: string;
}
