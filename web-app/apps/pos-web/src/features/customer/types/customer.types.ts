import type { QueryParams } from "@hvt/api-client";

export type CustomerStatus = "ACTIVE" | "INACTIVE" | "SUSPENDED";
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
    creditLimit: number;
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
    tierId?: string | null;
    creditLimit?: number | null;
    lastPurchaseDate?: string | null;
    totalSpent?: number | null;
    status?: CustomerStatus | null;
}

export interface CustomerTierResponse {
    id: string;
    code: string;
    name: string;
    minSpending: number;
    discountRate: number;
    status: "ACTIVE" | "INACTIVE";
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
