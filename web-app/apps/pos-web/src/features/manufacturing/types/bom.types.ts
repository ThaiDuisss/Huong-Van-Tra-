import type { CatalogStatus } from "../../product/types/product.types";

// ============================================================
// Types for API
// ============================================================

export type BomStatus =
    | "ACTIVE"
    | "PENDING_APPROVAL"
    | "MATERIAL_SHORTAGE"
    | "NEEDS_UPDATE"
    | "INACTIVE";

export interface BomItemResponse {
    id: string;
    ingredient: {
        id: string;
        ingredientCode: string;
        name: string;
        status: CatalogStatus;
    };
    quantity: number;
    unit: {
        id: string;
        name: string;
        symbol: string;
        precisionScale: number;
        type: "BASE" | "PACKAGING";
        baseUnitId: string | null;
        conversionRate: number;
        status: CatalogStatus;
    };
    wasteRate: number;
}

export interface BomResponse {
    id: string;
    productVariantId: string;
    variantSku: string;
    variantName: string;
    productName: string;
    name: string;
    bomCode: string;
    isDefault: boolean;
    version: number;
    status: BomStatus;
    description: string | null;
    effectiveFrom: string | null;
    effectiveTo: string | null;
    createdAt: string;
    updatedAt: string;
    items: BomItemResponse[];
}

export interface BomItemInput {
    ingredientId: string;
    quantity: number;
    unitId: string;
    wasteRate: number;
}

export interface BomInput {
    productVariantId: string;
    name: string | null;
    bomCode: string | null;
    isDefault: boolean;
    version: number | null;
    status: BomStatus;
    description: string | null;
    items: BomItemInput[];
}

export interface BomQuery {
    page?: number;
    size?: number;
    search?: string;
    categoryId?: string;
    version?: number;
    status?: BomStatus;
}

export interface LookupItem {
    id: string;
    name: string;
}

// ============================================================
// Backward compatibility for mock data and shared components
// ============================================================

export type BomProductType = "PACKAGED_TEA" | "GIFT_SET" | "ACCESSORY";

export type BomUpdatedRange = "ALL" | "TODAY" | "LAST_7_DAYS" | "LAST_30_DAYS" | "CUSTOM";

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
