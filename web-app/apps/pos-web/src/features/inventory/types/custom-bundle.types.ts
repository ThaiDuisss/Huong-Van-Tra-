import type {
    InventoryAuditEvent,
    InventoryLocationSummary,
    UserSnapshot
} from "./stock-document.types";

export type CustomBundleStatus =
    | "DRAFT"
    | "AWAITING_RESERVATION"
    | "RESERVED"
    | "RESERVATION_FAILED"
    | "AWAITING_PAYMENT"
    | "PAID"
    | "PACKING"
    | "PACKED"
    | "CANCELLED"
    | "FAILED";

export type BundlePaymentStatus = "PENDING" | "SUCCESS" | "FAILED" | "REFUND_REQUIRED";
export type BundleReservationStatus = "NONE" | "RESERVED" | "FAILED" | "RELEASED" | "COMMITTED" | "EXPIRED";
export type CustomBundleRole = "SALES_STAFF" | "BRANCH_MANAGER" | "WAREHOUSE" | "ADMIN" | "ACCOUNTANT" | "QC_STAFF";

export interface BundleIngredientOption {
    id: string;
    category: string;
    skuCode: string;
    name: string;
    unit: "g" | "kg" | "túi";
    branchAvailableQuantity: number;
    centralAvailableQuantity: number;
    pricePerGram: number;
    nearLowStock: boolean;
    blocked: boolean;
    expired: boolean;
    alternativeSkuId?: string;
}

export interface CustomBundleLine {
    id: string;
    ingredientId: string;
    skuCode: string;
    ingredientName: string;
    category: string;
    unit: "g" | "kg" | "túi";
    quantity: number;
    availableQuantity: number;
    price: number;
    batchAllocations: CustomBundleBatchAllocation[];
}

export interface CustomBundleBatchAllocation {
    id: string;
    batchCode: string;
    expiryDate: string;
    locationName: string;
    reservedQuantity: number;
    committedQuantity: number;
}

export interface CustomBundleSummary {
    id: string;
    code: string;
    orderCode?: string;
    customerName: string;
    branch: InventoryLocationSummary;
    totalIngredients: number;
    totalWeight: number;
    estimatedPrice: number;
    paymentStatus: BundlePaymentStatus;
    status: CustomBundleStatus;
    requestedPickupAt?: string;
    createdAt: string;
}

export interface CustomBundleDetail extends CustomBundleSummary {
    displayName: string;
    bundleName?: string;
    packagingType: string;
    targetWeight: number;
    usagePurpose: "GIFT" | "PERSONAL";
    packingNote?: string;
    lines: CustomBundleLine[];
    reservationStatus: BundleReservationStatus;
    reservationExpiresAt?: string;
    auditEvents: InventoryAuditEvent[];
    createdBy: UserSnapshot;
    stockDocumentId?: string;
    stockDocumentCode?: string;
    numberOfPackages?: number;
    actualTotalWeight?: number;
    refundStatus?: string;
}

export interface CustomBundleListQuery {
    page: number;
    size: number;
    tab: "AWAITING_PAYMENT" | "PAID" | "PACKING" | "PACKED" | "FAILED";
    code: string;
    orderCode: string;
    locationId: "ALL" | string;
    status: "ALL" | CustomBundleStatus;
    pickupFrom: string;
    pickupTo: string;
    customerName: string;
}

export interface CustomBundleListResult {
    content: CustomBundleSummary[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    first: boolean;
    last: boolean;
}

export interface CustomBundleCreateInput {
    branchId: string;
    customerName: string;
    bundleName?: string;
    packagingType: string;
    targetWeight: number;
    displayName: string;
    usagePurpose: "GIFT" | "PERSONAL";
    requestedPickupAt: string;
    packingNote?: string;
    lines: Array<{ ingredientId: string; quantity: number; unit: "g" | "kg" | "túi" }>;
}

export interface BundlePackingInput {
    bundleId: string;
    operationId: string;
    packagingType: string;
    numberOfPackages: number;
    actualTotalWeight: number;
    packingNote?: string;
    lines: Array<{
        lineId: string;
        actualPackedQuantity: number;
        picked: boolean;
        reason?: string;
    }>;
}

export interface CustomBundleOptions {
    ingredients: BundleIngredientOption[];
    locations: InventoryLocationSummary[];
}

export interface CustomBundleApi {
    createCustomBundle(input: CustomBundleCreateInput): Promise<CustomBundleDetail>;
    updateCustomBundle(bundleId: string, input: CustomBundleCreateInput): Promise<CustomBundleDetail>;
    reserveCustomBundle(bundleId: string): Promise<CustomBundleDetail>;
    releaseCustomBundleReservation(bundleId: string): Promise<CustomBundleDetail>;
    listCustomBundles(query: CustomBundleListQuery): Promise<CustomBundleListResult>;
    getCustomBundle(bundleId: string): Promise<CustomBundleDetail>;
    startCustomBundlePacking(bundleId: string, actorRole?: CustomBundleRole): Promise<CustomBundleDetail>;
    confirmCustomBundlePacking(input: BundlePackingInput): Promise<CustomBundleDetail>;
    cancelCustomBundle(bundleId: string, actorRole?: CustomBundleRole): Promise<CustomBundleDetail>;
    getCustomBundleOptions(): Promise<CustomBundleOptions>;
    mockPaymentResult(bundleId: string, success: boolean): Promise<CustomBundleDetail>;
}
