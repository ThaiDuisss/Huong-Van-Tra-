import type { CustomerStatus } from "./customer.types";

export type PurchasedFilter = "" | "true" | "false";

export interface CustomerFilterDraft {
    status: CustomerStatus | "";
    tierIds: string[];
    createdFrom: string;
    createdTo: string;
    lastPurchaseFrom: string;
    lastPurchaseTo: string;
    includeNeverPurchased: boolean;
    hasPurchased: PurchasedFilter;
    birthMonth: string;
    birthYear: string;
}

export type CustomerQuickViewId =
    | "all"
    | "new"
    | "recent"
    | "vip"
    | "never-purchased"
    | "birthday"
    | "inactive";

export interface AppliedCustomerFilter {
    key: string;
    label: string;
}
