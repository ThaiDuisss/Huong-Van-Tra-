export type ProductionOrderStatus =
    | "DRAFT"
    | "IN_PROGRESS"
    | "WAITING_MATERIAL"
    | "NEAR_COMPLETION"
    | "COMPLETED"
    | "OVERDUE"
    | "CANCELLED";

export type ProductionWorkshop =
    | "PACKAGING"
    | "MIXING"
    | "GIFT_ASSEMBLY";

export type ProductionOrderDateRange =
    | "ALL"
    | "TODAY"
    | "LAST_7_DAYS"
    | "LAST_30_DAYS"
    | "CUSTOM";

export interface ProductionMaterialUsage {
    id: string;
    name: string;
    required: string;
    issued: string;
}

export interface ProductionLogEntry {
    id: string;
    time: string;
    message: string;
    actor: string;
}

export interface ProductionOrderListItem {
    id: string;
    code: string;
    productName: string;
    productSku: string;
    productImageUrl: string;
    bomCode: string;
    plannedQuantity: number;
    completedQuantity: number;
    unit: string;
    startDate: string;
    dueDate: string;
    status: ProductionOrderStatus;
    owner: string;
    workshop: ProductionWorkshop;
    materialUsage: ProductionMaterialUsage[];
    logs: ProductionLogEntry[];
}

export interface ProductionOrderFiltersState {
    statuses: ProductionOrderStatus[];
    workshops: ProductionWorkshop[];
    dateRange: ProductionOrderDateRange;
}

// ============================================================
// Types khớp trực tiếp với API thật của Inventory-Service
// (dùng cho màn Tạo / Chi tiết lệnh sản xuất)
// ============================================================

export type ProductionOrderApiStatus =
    | "DRAFT"
    | "PENDING_APPROVAL"
    | "APPROVED"
    | "RELEASED"
    | "IN_PROGRESS"
    | "COMPLETED"
    | "CANCELLED";

export interface BomOption {
    id: string;
    bomCode: string;
    name: string;
    isDefault: boolean;
    bomVersion: number | null;
}

export interface ProductionOrderLineResponse {
    id: string;
    ingredientId: string | null;
    ingredientName: string | null;
    requiredQuantity: number;
    unitId: string | null;
    wasteRate: number | null;
    reservedQuantity: number;
    consumedQuantity: number;
}

export interface ProductionOrder {
    id: string;
    orderCode: string;
    status: ProductionOrderApiStatus;
    productVariantId: string;
    bomId: string;
    bomVersion: number | null;
    plannedQuantity: number;
    unitId: string | null;
    locationId: string;
    note: string | null;
    actualGoodQuantity: number | null;
    actualRejectedQuantity: number | null;
    finishedBatchId: string | null;
    lines: ProductionOrderLineResponse[];
    submittedBy: string | null;
    submittedAt: string | null;
    approvedBy: string | null;
    approvedAt: string | null;
    releasedBy: string | null;
    releasedAt: string | null;
    startedBy: string | null;
    startedAt: string | null;
    completedBy: string | null;
    completedAt: string | null;
    cancelledBy: string | null;
    cancelledAt: string | null;
    cancelReason: string | null;
    createdAt: string;
    updatedAt: string;
}

export interface ProductionOrderQuery {
    status?: ProductionOrderApiStatus;
    productVariantId?: string;
    search?: string;
    page?: number;
    size?: number;
}

export interface ProductionOrderCreateInput {
    productVariantId: string;
    bomId?: string;
    plannedQuantity: number;
    locationId: string;
    note?: string;
}

export interface ProductionOrderCompleteInput {
    actualGoodQuantity: number;
    actualRejectedQuantity: number;
    note?: string;
}
