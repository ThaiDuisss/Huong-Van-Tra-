export type BomStatus =
    | "ACTIVE"
    | "PENDING_APPROVAL"
    | "MATERIAL_SHORTAGE"
    | "NEEDS_UPDATE"
    | "INACTIVE";

export type BomProductType =
    | "PACKAGED_TEA"
    | "GIFT_SET"
    | "ACCESSORY";

export type BomUpdatedRange =
    | "ALL"
    | "TODAY"
    | "LAST_7_DAYS"
    | "LAST_30_DAYS"
    | "CUSTOM";

export interface BomComponent {
    id: string;
    name: string;
    group: string;
    quantity: number;
    unit: string;
    cost: number;
}

export interface BomHistoryEntry {
    id: string;
    date: string;
    description: string;
    actor: string;
}

export interface BomListItem {
    id: string;
    code: string;
    name: string;
    productName: string;
    productSku: string;
    productType: BomProductType;
    productImageUrl: string;
    version: string;
    componentCount: number;
    materialCost: number;
    packagingCost: number;
    totalCost: number;
    updatedAt: string;
    effectiveDate: string;
    status: BomStatus;
    components: BomComponent[];
    history: BomHistoryEntry[];
}

export interface BomFiltersState {
    productTypes: BomProductType[];
    statuses: BomStatus[];
    versions: string[];
    updatedRange: BomUpdatedRange;
}

export interface BomSummaryMetric {
    id: string;
    label: string;
    value: string;
    change: string;
    tone: "green" | "orange";
    trend: number[];
}
