// Dùng chung bởi StatusBadge (components/shared/StatusBadge.tsx) cho các trang
// chưa liên quan tới Lệnh sản xuất thật (RawMaterialListPage, SupplierListPage...).
export type ProductionOrderStatus =
    | "DRAFT"
    | "IN_PROGRESS"
    | "WAITING_MATERIAL"
    | "NEAR_COMPLETION"
    | "COMPLETED"
    | "OVERDUE"
    | "CANCELLED";

// ============================================================
// Types khớp trực tiếp với API thật của Inventory-Service
// (dùng cho màn Tạo / Chi tiết lệnh sản xuất)
// ============================================================

export type ProductionOrderApiStatus =
    | "DRAFT"
    | "PENDING_APPROVAL"
    | "APPROVED"
    | "REJECTED"
    | "CANCELLED";

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
    plannedQuantity: number;
    unitId: string | null;
    locationId: string;
    note: string | null;
    lines: ProductionOrderLineResponse[];
    submittedBy: string | null;
    submittedAt: string | null;
    approvedBy: string | null;
    approvedAt: string | null;
    rejectedBy: string | null;
    rejectedAt: string | null;
    cancelledBy: string | null;
    cancelledAt: string | null;
    cancelReason: string | null;
    expectedCompletionDate: string | null;
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
    plannedQuantity: number;
    locationId: string;
    note?: string;
}
