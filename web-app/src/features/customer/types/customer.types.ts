import type { QueryParams } from "@/lib/api-client";

export type CustomerStatus = "ACTIVE" | "INACTIVE" | "SUSPENDED";
export type CustomerTierStatus = "ACTIVE" | "INACTIVE";
export type LoyaltyTransactionType = "EARN" | "REVERSE" | "ADJUST" | "REDEEM";
export type LoyaltyReferenceType = "ORDER" | "REFUND" | "MANUAL";
export type CustomerSort =
    | "lastPurchaseDesc"
    | "lastPurchaseAsc"
    | "createdDesc"
    | "createdAsc"
    | "nameAsc"
    | "nameDesc";

export interface CustomerResponse {
    id: string;
    customerCode: string;
    fullName: string;
    phone: string;
    email: string | null;
    dateOfBirth: string | null;
    tierId: string | null;
    tierCode: string | null;
    tierName: string | null;
    tierColor: string | null;
    lastPurchaseDate: string | null;
    totalSpent: number;
    totalOrderCount: number;
    pointBalance: number;
    status: CustomerStatus;
    createdAt: string;
    /** Chỉ có ở GET /customer/{id}: hạng kế tiếp và phần còn thiếu để lên hạng. */
    nextTier?: CustomerTierResponse | null;
    remainingSpending?: number | null;
    remainingOrderCount?: number | null;
}

export interface CreateCustomerRequest {
    customerCode?: string | null;
    fullName: string;
    phone: string;
    email?: string | null;
    dateOfBirth?: string | null;
    status?: CustomerStatus | null;
}

export interface CustomerTierResponse {
    id: string;
    code: string;
    name: string;
    minSpending: number;
    minOrderCount: number;
    pointMultiplier: number;
    status: CustomerTierStatus;
    colorCode: string;
}

export interface CustomerTierRequest {
    code: string;
    name: string;
    minSpending: number;
    minOrderCount: number;
    pointMultiplier: number;
    status: CustomerTierStatus;
    colorCode: string;
}

export interface CustomerTierCountResponse {
    id: string | null;
    code: string;
    name: string;
    colorCode: string | null;
    customerCount: number;
}

export interface PageResponse<T> {
    content: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    first: boolean;
    last: boolean;
}

export interface LoyaltyTransactionResponse {
    id: string;
    customerId: string;
    type: LoyaltyTransactionType;
    points: number;
    balanceAfter: number;
    referenceType: LoyaltyReferenceType;
    /** Mã hiển thị của chứng từ (mã đơn / mã phiếu hoàn), không phải UUID. */
    referenceCode: string | null;
    reason: string | null;
    createdAt: string;
}

export interface LoyaltyAdjustmentRequest {
    /** Số điểm cộng (dương) hoặc trừ (âm), khác 0. */
    points: number;
    reason: string;
}

export interface ChangeTierStatusRequest {
    status: CustomerTierStatus;
}

export interface ChangeCustomerStatusRequest {
    status: CustomerStatus;
}

export type CustomerImportRowStatus = "VALID" | "IMPORTED" | "INVALID" | "FAILED";

export interface CustomerImportRow {
    rowNumber: number;
    customerCode?: string | null;
    fullName?: string | null;
    phone?: string | null;
    email?: string | null;
    dateOfBirth?: string | null;
}

export interface CustomerImportRequest {
    rows: CustomerImportRow[];
    dryRun: boolean;
}

export interface CustomerImportRowResult {
    rowNumber: number;
    status: CustomerImportRowStatus;
    message: string | null;
    customerId: string | null;
    customerCode: string | null;
    fullName: string | null;
    phone: string | null;
}

export interface CustomerImportResponse {
    dryRun: boolean;
    totalRows: number;
    successCount: number;
    failedCount: number;
    results: CustomerImportRowResult[];
}

export interface CustomerQuery extends QueryParams {
    q?: string;
    status?: CustomerStatus;
    tierIds?: string[];
    createdFrom?: string;
    createdTo?: string;
    lastPurchaseFrom?: string;
    lastPurchaseTo?: string;
    includeNeverPurchased?: boolean;
    minTotalSpent?: number;
    maxTotalSpent?: number;
    hasPurchased?: boolean;
    birthMonth?: number;
    birthYear?: number;
    vipTierIds?: string[];
    sort?: CustomerSort;
    page?: number;
    size?: number;
}
