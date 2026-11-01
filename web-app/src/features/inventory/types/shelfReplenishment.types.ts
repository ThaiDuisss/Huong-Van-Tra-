import type { QueryParams } from "@/lib/api-client";

/** Trạng thái phiếu nhập kệ (dùng lại InventoryDocumentStatus của backend). */
export type ShelfReplenishmentStatus =
    | "DRAFT"
    | "PENDING_APPROVAL"
    | "POSTED"
    | "REJECTED"
    | "CANCELLED";

export interface ShelfReplenishmentLine {
    id: string;
    lineNo: number;
    inventoryItemId: string;
    productVariantId: string;
    sku: string | null;
    itemName: string;
    unitId: string | null;
    quantity: number;
    transferredQuantity: number;
    warehouseAvailable: number;
    shelfOnHand: number;
    note: string | null;
}

export interface ShelfReplenishment {
    id: string;
    documentCode: string;
    status: ShelfReplenishmentStatus;
    branchId: string | null;
    warehouseId: string | null;
    warehouseName: string | null;
    shelfId: string | null;
    shelfName: string | null;
    referenceId: string | null;
    note: string | null;
    totalQuantity: number;
    lineCount: number;
    createdBy: string | null;
    createdAt: string;
    updatedAt: string | null;
    postedBy: string | null;
    postedAt: string | null;
    /** Phiếu do POS xuất kho ngay khi bán, không chờ kho xác nhận trước. */
    autoIssued: boolean;
    /** Đã xuất hàng nhưng manager chưa duyệt lại. */
    pendingReview: boolean;
    /** Người duyệt phiếu (hoặc manager duyệt lại phiếu POS xuất ngay). */
    reviewedBy: string | null;
    reviewedAt: string | null;
    lines: ShelfReplenishmentLine[];
    rejectedBy: string | null;
    rejectedAt: string | null;
    rejectionReason: string | null;
}

export interface ShelfReplenishmentSummary {
    draft: number;
    pending: number;
    posted: number;
    cancelled: number;
    rejected: number;
    /** Đã xuất hàng (POS xuất ngay khi bán) nhưng manager chưa duyệt. */
    pendingReview: number;
}

export interface ShelfReplenishmentLineInput {
    productVariantId: string;
    quantity: number;
    note?: string;
}

export interface ShelfReplenishmentCreateInput {
    shelfId?: string | null;
    warehouseId?: string | null;
    branchId?: string | null;
    /** Tham chiếu (vd. mã đơn POS). Cùng tham chiếu thì backend gộp vào phiếu còn mở. */
    referenceId?: string | null;
    note?: string | null;
    /** true: gửi kho xử lý ngay; false: lưu nháp để điền sau. */
    submit: boolean;
    /** true (POS): chuyển hàng lên kệ ngay trong phiếu riêng, quản lý duyệt lại sau. */
    issueNow?: boolean;
    lines: ShelfReplenishmentLineInput[];
}

export interface ShelfReplenishmentUpdateInput {
    shelfId?: string | null;
    warehouseId?: string | null;
    note?: string | null;
    lines: ShelfReplenishmentLineInput[];
}

export interface ShelfReplenishmentQuery extends QueryParams {
    page?: number;
    size?: number;
    status?: ShelfReplenishmentStatus;
    shelfId?: string;
    query?: string;
    /** true: chỉ lấy phiếu đã xuất hàng mà chưa được duyệt. */
    pendingReview?: boolean;
}
