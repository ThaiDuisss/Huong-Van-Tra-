import type { QueryParams } from "@/lib/api-client";

export type CustomerStatus = "ACTIVE" | "INACTIVE" | "SUSPENDED";
export type CustomerType = "INDIVIDUAL" | "BUSINESS";
export type CustomerTierStatus = "ACTIVE" | "INACTIVE";
export type CustomerTierAssignmentReason =
    | "INITIAL_TIER"
    | "YEARLY_QUALIFICATION"
    | "YEARLY_REVIEW"
    | "MANUAL_ADJUSTMENT";
export type CustomerTierAssignmentStatus =
    | "ACTIVE"
    | "REPLACED"
    | "EXPIRED"
    | "CANCELLED";
export type CustomerPurchaseTransactionType =
    | "PURCHASE"
    | "PARTIAL_REFUND"
    | "FULL_REFUND"
    | "ADJUSTMENT";
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
    customerType: CustomerType;
    tierId: string | null;
    tierCode: string | null;
    tierName: string | null;
    tierColor: string | null;
    lastPurchaseDate: string | null;
    totalSpent: number;
    status: CustomerStatus;
    createdAt: string;
}

export interface CreateCustomerRequest {
    customerCode?: string | null;
    fullName: string;
    phone: string;
    email?: string | null;
    dateOfBirth?: string | null;
    customerType?: CustomerType | null;
    tierId?: string | null;
    lastPurchaseDate?: string | null;
    totalSpent?: number | null;
    status?: CustomerStatus | null;
}

export interface CustomerTierResponse {
    id: string;
    code: string;
    name: string;
    minSpending: number;
    minOrderCount: number;
    status: CustomerTierStatus;
    colorCode: string;
}

export interface CustomerTierRequest {
    code: string;
    name: string;
    minSpending: number;
    minOrderCount: number;
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

export interface TierProgressResponse {
    currentTier: CustomerTierResponse | null;
    qualifiedTierForCurrentYear: CustomerTierResponse | null;
    currentYear: number;
    totalSpent: number;
    totalOrderCount: number;
    nextTier: CustomerTierResponse | null;
    remainingSpending: number | null;
    remainingOrderCount: number | null;
    currentTierEffectiveUntil: string | null;
}

export interface CustomerTierAssignmentResponse {
    id: string;
    customerId: string;
    tier: CustomerTierResponse | null;
    qualificationYear: number | null;
    assignmentReason: CustomerTierAssignmentReason;
    effectiveFrom: string;
    effectiveUntil: string | null;
    status: CustomerTierAssignmentStatus;
    note: string | null;
}

export interface CustomerTierYearlyMetricResponse {
    id: string;
    customerId: string;
    tierYear: number;
    totalSpent: number;
    totalOrderCount: number;
    qualifiedTier: CustomerTierResponse | null;
    calculatedAt: string;
}

export interface CustomerPurchaseTransactionResponse {
    id: string;
    customerId: string;
    orderId: string | null;
    referenceId: string | null;
    transactionType: CustomerPurchaseTransactionType;
    amount: number;
    orderCountDelta: number;
    occurredAt: string;
    reason: string | null;
    metadata: string | null;
}

export interface ManualTierAdjustmentRequest {
    tierId: string;
    reason: string;
    effectiveFrom?: string | null;
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
    customerType?: string | null;
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
